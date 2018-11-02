/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * 共享实例的公共注册表，实现SingletonBeanRegistry接口，允许通过beanName去获取注册的单利实例
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * 还支持注册{@link org.springframework.beans.factory.DisposableBean}实例（可能与注册的单例相对应，也可能不对应），
 * 在注册表关闭时销毁。 可以注册bean之间的依赖关系以强制执行适当的关闭顺序。
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * 该类主要用作{@link org.springframework.beans.factory.BeanFactory}实现的基类，分解了单例bean实例的通用管理。
 * 请注意，{@link org.springframework.beans.factory.config.ConfigurableBeanFactory}接口扩展了{@link SingletonBeanRegistry}接口。
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * 请注意，与{@link AbstractBeanFactory}和{@link DefaultListableBeanFactory}（继承自它）相比，
 * 此类既不假定bean定义概念也不假定bean实例的特定创建过程。 或者也可以用作委托的嵌套助手。
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 * 默认的单利bean注册器,对单利bean接口和别名的实现,因为是共享单利,所以这个类最好在整个项目中只有一份实例
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/**
	 * Internal marker for a null singleton object:
	 * used as marker value for concurrent Maps (which don't support null values).
	 */
	protected static final Object NULL_OBJECT = new Object();


	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Cache of singleton objects: bean name --> bean instance
	 *  用来缓存单利的对象，beanName->bean instatnce的映射关系,初始化大小为2^8
	 *  */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<String, Object>(256);

	/** Cache of singleton factories: bean name --> ObjectFactory
	 * 用来缓存单利的工厂,beanName -> ObjectFactory
	 *
	 * */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>(16);

	/** Cache of early singleton objects: bean name --> bean instance
	 *  缓存早期的单利对象
	 * */
	private final Map<String, Object> earlySingletonObjects = new HashMap<String, Object>(16);

	/** Set of registered singletons, containing the bean names in registration order
	 * 注册单利名称集合，保存单利映射的beanName
	 * */
	private final Set<String> registeredSingletons = new LinkedHashSet<String>(256);

	/** Names of beans that are currently in creation
	 * 当前正在创建的bean的名称
	 * */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));

	/** Names of beans currently excluded from in creation checks
	 * 当前从创建检查中排除的bean的名称
	 * */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>(16));

	/** List of suppressed Exceptions, available for associating related causes
	 * 被抑制的异常列表，可用于关联相关原因
	 * */
	private Set<Exception> suppressedExceptions;

	/** Flag that indicates whether we're currently within destroySingletons
	 * 指示我们当前是否在destroySingletons中的标志
	 * */
	private boolean singletonsCurrentlyInDestruction = false;

	/** Disposable bean instances: bean name --> disposable instance
	 * 为bean指定相应的DisposableBean的实例,实际上就是为bean单独指定销毁对象实例
	 * */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<String, Object>();

	/** Map between containing bean names: bean name --> Set of bean names that the bean contains
	 *  包含bean名称之间的映射：bean name - > bean包含的bean1名称集，用于保存两个bean之间的包含关系
	 *  {beanName:[containedBeanName,containedBeanName1]}
	 * */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<String, Set<String>>(16);

	/** Map between dependent bean names: bean name --> Set of dependent bean names
	 * 依赖bean名称之间的映射：bean name - >依赖bean名称的集合
	 * {beanName:[dependencyBean,dependencyBean1,dependencyBean2]},就是保存这个bean下所有的依赖的bean
	 * */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<String, Set<String>>(64);

	/** Map between depending bean names: bean name --> Set of bean names for the bean's dependencies
	 * 依赖bean名称之间的映射：bean name - > bean依赖项的bean名称集
	 * {dependencyBean:[beanName,beanName1,beanName2]},就是保存含有这个依赖的所有bean
	 * */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<String, Set<String>>(64);

	/**
	 *
	 * @param beanName the name of the bean
	 * @param singletonObject the existing singleton object
	 * @throws IllegalStateException
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "'beanName' must not be null");
		synchronized (this.singletonObjects) {
			//从缓存中获取对应的bean,如果存在则不允许注册
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * Add the given singleton object to the singleton cache of this factory.
	 * 将给定的singleton对象添加到此工厂的singleton缓存中。
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			//添加单利
			this.singletonObjects.put(beanName, (singletonObject != null ? singletonObject : NULL_OBJECT));
			//移除
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * 如果需要，添加给定的单利工厂用于构建指定的单例。可以调用单例的单个注册，例如， 能够解决循环引用。
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

	@Override
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 *
	 * 返回在给定名称下注册的（原始）单例对象。检查已经实例化的单例并且还允许早期引用当前创建的单例（解析循环引用）。
	 *
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		Object singletonObject = this.singletonObjects.get(beanName);
		//如果不存在该单利,或者这个单利正在创建中
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			synchronized (this.singletonObjects) {
				//则获取更早创建的单利
				singletonObject = this.earlySingletonObjects.get(beanName);
				//早期引用是否允许引用当前的单利对象
				if (singletonObject == null && allowEarlyReference) {
					//获取对应的ObjectFactory
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						//通过ObjectFactory来获取单利对象,在调用getObject方法时，会通过BeanFactory的子类去获取bean实例，如没有则会去创建bean对象
						//所以说,最终还是会有一个对象，如果实在没有则返回NULL_OBJECT
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return (singletonObject != NULL_OBJECT ? singletonObject : null);
	}

	/**
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * 返回在给定名称下注册的（原始）单例对象，如果尚未注册，则创建并注册新对象。
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "'beanName' must not be null");
		synchronized (this.singletonObjects) {
			//获取单利对象
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				//当前单利是否处于销毁中
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				//单利创建前的回调,判断beanName是否已经在创建中了,是则抛出异常,否,则继续下去
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<Exception>();
				}
				try {
					//通过ObjectFactory工厂，从BeanFactory中获取创建的Bean对象
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					//创建单利对象之后的回调
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					//添加bean到单利map中
					addSingleton(beanName, singletonObject);
				}
			}
			return (singletonObject != NULL_OBJECT ? singletonObject : null);
		}
	}

	/**
	 * Register an Exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * @param ex the Exception to register
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 *
	 * 从该工厂的单例缓存中删除具有给定名称的bean，以便在创建失败时清除单个的单独注册。
	 *
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	/**
	 * 返回所有单利的beanName
	 * @return
	 */
	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	/**
	 * 返回所有单利的个数
	 * @return
	 */
	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}

	/**
	 * 根据inCreation 标记当前beanName的单利是在创建中还是已创建
	 * @param beanName
	 * @param inCreation
	 */
	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			//不在创建中,则添加到非创建中的集合里
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			//在创建中,则从inCreationCheckExclusions中移除
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	/**
	 * 判断当前beanName对应的bean是否在创建中
	 * @param beanName
	 * @return
	 */
	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * Callback before singleton creation.
	 * 单例创建之前的回调
	 * <p>The default implementation register the singleton as currently in creation.
	 * 默认实现将单例注册为当前创建中。 子类可以重写这个方法,在创建单利之前做一些相关的处理
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		//this.inCreationCheckExclusions.contains(beanName)  判断beanName是否不在创建中
		//this.singletonsCurrentlyInCreation.add(beanName) 将当前bean添加到当前创建中的集合列表中
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			//抛出当前bean在创建中的异常
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * 创建单利对象之后的回调
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * 默认实现将单例标记为不再在创建中。 子类可以重写这个方法,在创建完后,做一些相关的处理
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		//this.inCreationCheckExclusions.contains(beanName) 判断beanName是否不在创建中
		//this.singletonsCurrentlyInCreation.remove(beanName) 单利创建完后,将单利标记为不在创建中的状态
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			//只有单利在创建中，才能标记为不在创建中的状态
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 *
	 * 将给定的bean添加到此注册表中的一次性bean列表中.Disposable bean通常对应于已注册的单例，
	 * 与bean名称匹配但可能是不同的实例（例如，对于不自然实现Spring的DisposableBean接口的单例的DisposableBean适配器））。
	 *
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 *
	 * 为这个beanName注册指定的DisposableBean
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * 在两个bean之间注册一个包含关系，例如。 在一个内部bean和外部bean之间。 就根据销毁顺序而言注册的外部bean依赖于内部bean。
	 * @param containedBeanName the name of the contained (inner) bean 内部bean,被包含的bean
	 * @param containingBeanName the name of the containing (outer) bean 外部bean
	 * @see #registerDependentBean
	 *
	 * 注册两个bean的包含关系，如Person 对象中含有Dog的对象
	 * class Dog{}
	 * class Person{Dog dog}
	 *
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			//通过外部bean获取它的内部依赖的bean的集合
			Set<String> containedBeans = this.containedBeanMap.get(containingBeanName);
			if (containedBeans == null) {
				containedBeans = new LinkedHashSet<String>(8);
				//如果两者之间还没有保存关系,则添加新的关系
				this.containedBeanMap.put(containingBeanName, containedBeans);
			}
			//这个集合添加被依赖的内部bean
			containedBeans.add(containedBeanName);
		}
		//注册两者的依赖关系,便于在销毁时,先销毁被依赖的bean
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * 为给定的bean注册一个依赖bean，在销毁给定bean之前销毁依赖的bean。
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 *
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		//返回原始的beanName,因为这里传递的beanName有可能是别名
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			//从已有的依赖中获取当前beanName下依赖的所有bean的name
			Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
			if (dependentBeans == null) {
				dependentBeans = new LinkedHashSet<String>(8);
				//
				this.dependentBeanMap.put(canonicalName, dependentBeans);
			}
			dependentBeans.add(dependentBeanName);
			//dependentBeanMap 最终数据为: {"beanName":["dependentBeanName","dependentBeanName1"]}
		}
		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(dependentBeanName);
			if (dependenciesForBean == null) {
				dependenciesForBean = new LinkedHashSet<String>(8);
				this.dependenciesForBeanMap.put(dependentBeanName, dependenciesForBean);
			}
			dependenciesForBean.add(canonicalName);
			//dependenciesForBeanMap 最终数据为：{"dependentBeanName",["beanName","beanName1"]}
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * 确定指定的依赖bean是否已注册为依赖于给定bean或其任何传递依赖项。
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		//获取指定beanName的依赖的bean的name
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}
		//包含,则表示beanName和dependentBeanName之间是有直接依赖关系的
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		//如果beanName和dependentBeanName之间没有直接的依赖关系,则判断是否有传递依赖关系（transitiveDependency）
		//如: a -> b -> c -> d  beanName就是a, 而dependentBeanName就是d ,name他们之间不是直接依赖，而是传递依赖
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<String>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * 确定是否已为给定名称注册了依赖bean。
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * 返回依赖于指定bean的所有bean的名称（如果有）。
	 * 就是获取这个bean下的所有依赖
	 * {beanName:[dependencyName1,dependencyName2]}
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			//将collection集合转换为String[]数组
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * 返回指定bean所依赖的所有bean的名称（如果有）。
	 * 就是获取含有这个依赖的所有bean的名称
	 * {dependencyName:[beanName1,beanName2]}
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	/**
	 * 销毁单利
	 */
	public void destroySingletons() {
		if (logger.isDebugEnabled()) {
			logger.debug("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			//标记为当前正在销毁单利
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			//disposableBeans key为单利的beanName，value为DisposableBean实例
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		//将所有
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			//传递单利beanName，所以代表要销毁指定的单利
			destroySingleton(disposableBeanNames[i]);
		}

		//将包含关系的bean缓存清空
		this.containedBeanMap.clear();
		//将bean下的依赖的bean缓存清空
		this.dependentBeanMap.clear();
		//将包含该依赖的所有bean的缓存清空
		this.dependenciesForBeanMap.clear();
		//清空单利集合
		clearSingletonCache();
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * 销毁给定的bean。 如果找到相应的销毁bean实例，则委托{@code destroyBean}。
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		// 从单利集合中移除该bean
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			//public V remove(Object key) {
			//Node<K, V> node = removeInternalByKey(key);
			//return node != null ? node.value : null;
			//}
			//因为这里的disposableBeans是linkedHashMap类型，链式结构，所以remove会返回移除的那个key对应的value,这里就是单利bean对应的那个DisposableBean实例
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * 销毁指定beanName的bean,必须在销毁指定beanName的bean之前将它依赖的bean先给销毁掉
	 * @param beanName the name of the bean  要销毁的单利bean
	 * @param bean the bean instance to destroy  给单利bean指定的销毁处理器
	 */
	protected void destroyBean(String beanName, DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			//这里的dependentBeanMap 是将该bean的所有依赖清空，下面操作dependentBeanMap，是将其他bean对该beanName的依赖清空
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			//在销毁指定Bean之前,需要先销毁这个bean锁依赖的其他bean实例
			//如 a->b->c  在销毁a之前，先销毁c,在销毁b，最后销毁a
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		//相关依赖都销毁了，那么,现在开始销毁指定的bean
		if (bean != null) {
			try {
				//如果给bean指定了销毁处理器，那么就调用销毁处理器的destroy()方法来销毁bean
				bean.destroy();
			}
			catch (Throwable ex) {
				logger.error("Destroy method on bean with name '" + beanName + "' threw an exception", ex);
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				//销毁包含的bean
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		//dependentBeanMap: {beanName:[dependentBean,dependentBean1]}
		//因为上面将当前beanName的依赖已经被销毁了,所以这里需要将其他bean对当前beanName的依赖进行清除处理
		//比如当前需要销毁beanName=a，但是beanName=b的bean对a有依赖，所以需要清除b中的dependentBeanMap对a的相关数据
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		//dependenciesForBeanMap : {dependencyName:[beanName,beanName1]}
		//这里的beanName对应key，所以是清除包含beanName依赖的Bean
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 *
	 * 将单例互斥体暴露给子类和外部协作者。如果子类执行任何类型的扩展单例创建阶段，
	 * 则子类应在给定的对象上同步。 特别是，子类不应该在单例创建中涉及自己的互斥锁，
	 * 以避免在惰性初始化情况下发生死锁的可能性。
	 *
	 * 在子类要通过这个方法获取数据时，需要对这个返回的对象进行加锁互斥，例如：synchronized (getSingletonMutex()) {}，从而保证singletonObjects对象的正确性
	 */
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
