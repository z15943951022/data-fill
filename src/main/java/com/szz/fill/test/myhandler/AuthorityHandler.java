package com.szz.fill.test.myhandler;

import com.szz.fill.datafill.handler.AbstractDataFillHandler;
import com.szz.fill.datafill.handler.DataFillHandler;
import com.szz.fill.datafill.metadata.DataFillMetadata;
import com.szz.fill.test.model.Authority;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author szz
 */
public class AuthorityHandler extends AbstractDataFillHandler {

    public static Map<Object, Authority> authMap = new ConcurrentHashMap<>();

    static {
        authMap.put("1",new Authority(101));
        authMap.put("2",new Authority(102));
        authMap.put("3",new Authority(103));
        authMap.put("4",new Authority(104));
    }

    @Override
    public DataFillHandler fill(DataFillMetadata metadata) throws Exception {
        Authority authority = authMap.get(metadata.getSelectionKey());
        if (null != authority){
            Field fillField = metadata.getFillField();
            fillField.setAccessible(true);
            fillField.set(metadata.getFillObj(),authority);
        }
        System.out.println(Thread.currentThread().getName()+": 开始填充权限等级");
        return this;
    }
}
