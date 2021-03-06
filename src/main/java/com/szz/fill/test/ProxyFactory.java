package com.szz.fill.test;

import com.szz.fill.datafill.executor.DataFillExecutor;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author szz
 */
public class ProxyFactory<T> implements MethodInterceptor {

    private T target;

    public ProxyFactory(T target) {
        this.target = target;
    }

    public T newInstance(T target) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(target.getClass());
        enhancer.setCallback(this);
        return (T)enhancer.create();
    }

    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        Object invoke = method.invoke(target, objects);

        DataFillExecutor.execute(invoke);
        return invoke;
    }

}
