package com.rao.study.spring;

import org.junit.Test;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringValueResolver;

import java.util.Properties;

public class SimpleAliasRegistryTest {

    @Test
    public void testResolveAliases1(){
        SimpleAliasRegistry aliasRegistry = new SimpleAliasRegistry();

        //第一种情况,name=alias,这种会将已存在的alias移除
        /**
         * if (alias.equals(name)) {
         *  this.aliasMap.remove(alias);
         * }
         */
        aliasRegistry.registerAlias("myBean1","myBean");
        aliasRegistry.registerAlias("myBean","myBean");
    }

    @Test
    public void testResolveAliases2(){
        SimpleAliasRegistry aliasRegistry = new SimpleAliasRegistry();

        //第二种情况,可以追加,name相同,alias不同,可以添加多个别名
        aliasRegistry.registerAlias("myBean","myBeanAlias");
        aliasRegistry.registerAlias("myBean","myBeanAlias1");
    }

    @Test
    public void testResolveAliases3(){
        SimpleAliasRegistry aliasRegistry = new SimpleAliasRegistry();

        //第三种情况,默认可以覆盖,alais相同,name不同,则后面添加的可以覆盖之前添加的,最终结果只有{myBeanAlias,myBean1} ,这种意思表示更改bean了
        aliasRegistry.registerAlias("myBean","myBeanAlias");
        aliasRegistry.registerAlias("myBean1","myBeanAlias");
    }

    @Test
    public void testResolveAliases4(){
        SimpleAliasRegistry aliasRegistry = new SimpleAliasRegistry();

        //第四种情况,checkForAliasCircle内部的判断，这个方法会将name和alias进行互换,也会将已有的alias与当前的name进行互换
        //1. name=a,alias=b name1=b,alias1=a 这种情况,不允许添加,会抛出循环引用的异常 ，就是registeredName.equals(name) 和registeredAlias.equals(alias) 这两个条件的满足
        // 即 name.equal(alias1) 和 alias.equal(name1)
        aliasRegistry.registerAlias("a","b");
        aliasRegistry.registerAlias("b","a");
    }

    @Test
    public void testResolveAliases5(){
        SimpleAliasRegistry aliasRegistry = new SimpleAliasRegistry();

        //第5种情况,checkForAliasCircle内部的判断，这个方法会将name和alias进行互换,也会将已有的alias与当前的name进行互换
        //1. name=a,alias=b name1=b,alias1=c,name2=c,alias2=a 这种情况,不允许添加,会抛出循环引用的异常 ，就是registeredName.equals(name) 和hasAlias(registeredAlias, alias) 这两个条件的满足
        //  name.equal(alias1) 和 hasAlias(alias,name1) 即深层次的循环引用
        aliasRegistry.registerAlias("a","b");
        aliasRegistry.registerAlias("b","c");
        aliasRegistry.registerAlias("c","a");
    }

    @Test
    public void resolveAliasesTest(){
        SimpleAliasRegistry aliasRegistry = new SimpleAliasRegistry();
        aliasRegistry.registerAlias("${beanName}","my${aliasName}");
        aliasRegistry.registerAlias("${beanName}","my${aliasName}1");

        Properties properties = new Properties();
        properties.setProperty("beanName","A");
        properties.setProperty("aliasName","B");


        PropertyPlaceholderHelper propertyPlaceholderHelper = new PropertyPlaceholderHelper("${", "}", ":", false);
        PropertyPlaceholderHelper.PlaceholderResolver resolver = new PropertyPlaceholderHelper.PlaceholderResolver() {
            @Override
            public String resolvePlaceholder(String placeholderName) {
                return (String) properties.get(placeholderName);
            }
        };
        //创建占位符处理器
        aliasRegistry.resolveAliases(new StringValueResolver() {
            @Override
            public String resolveStringValue(String strVal) {
                return propertyPlaceholderHelper.replacePlaceholders(strVal,resolver);
            }
        });
    }

}
