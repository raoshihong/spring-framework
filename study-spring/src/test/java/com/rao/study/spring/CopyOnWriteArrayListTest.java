package com.rao.study.spring;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteArrayListTest {

    @Test
    public void testCopyOnWriteArrayList(){
        //CopyOnWriteArrayList 是线程安全的,在add方法中加入了锁
        List<String> strings = new CopyOnWriteArrayList<>();
        strings.add("ss");
        strings.add("ff");
        System.out.println(strings);
    }

}
