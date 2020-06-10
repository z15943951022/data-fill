package com.szz.fill.datafill.executor;

import com.szz.fill.datafill.annonation.DataFill;
import com.szz.fill.datafill.annonation.DataFillEnable;
import com.szz.fill.datafill.handler.DataFillHandler;
import com.szz.fill.datafill.metadata.DataFillMetadata;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <h2>数据填充核心执行器</h2>
 * 该类将对象中的注解字段与对应的handler扫描出来，并为每个字段进行异步填充操作。
 * {@link com.szz.fill.datafill.executor.DataFillExecutor#tl} 此变量装载了每个字段的操作任务
 * {@link DataFillExecutor#execute0(java.lang.Object, java.util.Map)} 此方法为核心方法也是填充的入口
 *
 * @author szz
 */
public class DataFillExecutor {

    private static ConcurrentHashMap<String, DataFillHandler> handlerKeys = new ConcurrentHashMap();

    private static ThreadLocal<List<Callable<Object>>> tl = ThreadLocal.withInitial(() ->{
        List<Callable<Object>> fillGroup = new ArrayList<>();
        return fillGroup;
    });

    public static final ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 1,
                        TimeUnit.SECONDS,
                        new LinkedBlockingDeque<>(1),
                        Executors.defaultThreadFactory(),
                        new ThreadPoolExecutor.CallerRunsPolicy());


    /**
     * 对外暴露的执行模板,其意义在于配合ThreadLocal收集的异步任务调用,
     * {@link DataFillExecutor#executeAfter()} 统一执行
     * @param target
     */
    public static void execute(Object target) {
        execute0(target,new ConcurrentHashMap());
        executeAfter();
    }


    /**
     * 执行器的入口,扫描对象中的所有带{@link DataFill} 注解的字段并进行
     * 解析封装等操作
     *
     * @param target 填充对象
     * @param contextArgs 上下文的依赖参数
     */
    private static void execute0(Object target, final Map contextArgs) {
        Class<?> aClass = target.getClass();
        Field[] declaredFields = aClass.getDeclaredFields();

        for (Field declaredField : declaredFields) {
            DataFill dataFill = declaredField.getAnnotation(DataFill.class);
            if (null != dataFill) {

                DataFillMetadata metadata = new DataFillMetadata();
                metadata.setFillField(declaredField);
                metadata.setFillObj(target);
                metadata.setSelectionKey(findParam(dataFill, target, contextArgs));

                tl.get().add(() -> {
                    dispatcher(dataFill, metadata);
                    declaredField.setAccessible(true);
                    try {
                        Object sinkObj = declaredField.get(target);
                        if (null != sinkObj && null != sinkObj.getClass().getAnnotation(DataFillEnable.class)) {
                            sinkExecute(sinkObj, contextArgs);
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
            }
        }
    }



    /**
     * 此方法存在的意义在于更好的实现递归,
     * 与{@link DataFillExecutor#execute0(java.lang.Object, java.util.Map)} 并无太大差异
     * 只是取消了异步任务的添加。
     * 因为递归填充数据需要依赖上一个填充好的数据，所以并不能异步操作
     *
     * @param target 填充对象
     * @param contextArgs 上下文的依赖参数
     */
    private static void sinkExecute(Object target, final Map contextArgs) {
        Class<?> aClass = target.getClass();
        Field[] declaredFields = aClass.getDeclaredFields();

        for (Field declaredField : declaredFields) {
            DataFill dataFill = declaredField.getAnnotation(DataFill.class);
            if (null != dataFill) {

                DataFillMetadata metadata = new DataFillMetadata();
                metadata.setFillField(declaredField);
                metadata.setFillObj(target);
                metadata.setSelectionKey(findParam(dataFill, target, contextArgs));

                dispatcher(dataFill, metadata);
                declaredField.setAccessible(true);
                try {
                    Object sinkObj = declaredField.get(target);
                    if (null != sinkObj.getClass().getAnnotation(DataFillEnable.class)) {
                        sinkExecute(sinkObj, contextArgs);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * 当前填充的收尾工作,将收集到的填充任务统一执行
     */
    private static void executeAfter(){
        try {
            List<Future<Object>> futures = pool.invokeAll(tl.get());
            for (Future<Object> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }finally {
            tl.remove();
        }
    }


    /**
     * 这里类似享元模式,可以借鉴String 底层的字符串常量池原理。
     * 通过注解信息查找对应字段。
     *
     * @param dataFill 填充注解
     * @param target 目标对象
     * @param args 上下文参数
     * @return
     */
    private static Object findParam(DataFill dataFill, Object target, Map contextArgs) {
        String key = dataFill.value();
        Object value = null;
        try {
            Field field = target.getClass().getDeclaredField(key);
            field.setAccessible(true);
            value = field.get(target);
            if (value == null) throw new NoSuchFieldException();
            contextArgs.put(key, value);
        } catch (NoSuchFieldException e) {
            value = contextArgs.get(key);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return value;
    }


    /**
     * handler的分发器,不同的注解应对应不同的填充规则。
     * 这里也类似享元模式,这里将收集好的元信息分发给用户自定义的handler。
     *
     * @param dataFill 填充注解
     * @param dataFillMetadata 封装好的元信息
     */
    private static void dispatcher(DataFill dataFill, DataFillMetadata dataFillMetadata) {
        Class<? extends DataFillHandler> handler = dataFill.handler();
        DataFillHandler dataFillHandler;
        if ((dataFillHandler = handlerKeys.get(handler.getName())) != null) {
            try {
                dataFillHandler.fill0(dataFillMetadata);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        } else {
            try {
                DataFillHandler fillHandler = handler.newInstance();
                handlerKeys.put(handler.getName(), fillHandler);
                fillHandler.fill0(dataFillMetadata);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


}
