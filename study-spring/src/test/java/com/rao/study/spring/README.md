阅读源码，到对应的实现类时,可以写几个测试用例进行测试,通过断点调试进行了解内部原理
1.如果是阅读接口,则对每个接口的方法进行翻译备注,了解其定义的功能
2.如果是阅读实现类，则需要以下步骤：
    (1)对方法定义的功能进行翻译备注
    (2)编写测试方法,对实现类的相应方法进行断点调试,了解每个功能点
    
SimpleAliasRegistry 类
    别名的映射规则：
    1.如果beanName=alias,则会将之前已加入过的相同别名的bean记录移除
    2.一个beanName可以映射多个不同的alias
    3.如果不同beanName，但是alias相同的话，会将之前的beanName的映射移除,意思就是更改bean了
    4.name=a,alias=b name1=b,alias1=a 这种情况,不允许添加,会抛出循环引用的异常
    5.name=a,alias=b name1=b,alias1=c,name2=c,alias2=a 这种情况,不允许添加,会抛出循环引用的异常
    6.beanName和alias的名称可以使用占位符,会使用值处理器替换占位符

DefaultSingletonBeanRegistry类
    1.用来保存所有单利对象的，包括普通的单利和factoryBean的单利对象
    2.保存单利bean已经bean所依赖的其他bean之间的关系
    3.保存其他bean对当前bean的依赖的关系
    4.保存当前bean包含的其他bean的关系
    5.在获取单利对象时，会进行以下判断：
        如果不存在单利，则判断该单利是否正在创建，如果是，则获取更早的单利对象，如果没有则通过ObjectFactory进行创建获取
        获取单利时，提供了创建单利前的方法和创建单利后的方法的处理
        
在spring中有两种类的实例，一种是普通bean的实例，一种是factoryBean的实例，在spring中做了区分处理
        
