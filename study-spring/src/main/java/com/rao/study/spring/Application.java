package com.rao.study.spring;

import com.rao.study.spring.service.UserService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

public class Application {
    public static void main(String[] args){
        System.out.println("test");
        BeanFactory beanFactory = new XmlBeanFactory(new ClassPathResource("application.xml"));
        UserService userService = beanFactory.getBean(UserService.class);
        userService.test();
    }
}
