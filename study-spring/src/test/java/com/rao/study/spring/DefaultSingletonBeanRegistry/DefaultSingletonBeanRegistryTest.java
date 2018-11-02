package com.rao.study.spring.DefaultSingletonBeanRegistry;

import org.junit.Test;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;

public class DefaultSingletonBeanRegistryTest {

    @Test
    public void registerSingletonTest(){
        DefaultSingletonBeanRegistry beanRegistry = new DefaultSingletonBeanRegistry();
        beanRegistry.registerSingleton("person",new Person());
//        beanRegistry.registerSingleton("person",new Person());//因为是注册单利,所以不允许再次注册
        beanRegistry.registerSingleton("student",new Student());

    }

    @Test
    public void test(){
        //默认是不开启jvm安全的
        SecurityManager securityManager = System.getSecurityManager();
        System.out.println(securityManager);

        //开启jvm安全检测
        System.setSecurityManager(new SecurityManager());
        securityManager = System.getSecurityManager();

    }
}
