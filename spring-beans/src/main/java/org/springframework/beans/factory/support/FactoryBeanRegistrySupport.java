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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;


/**
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 *
 * 支持需要处理{@link org.springframework.beans.factory.FactoryBean}实例的单例注册表的基类，
 * 并与{@link DefaultSingletonBeanRegistry}的单例管理集成。
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 * 服务与{@link AbstractBeanFactory}的基类.
 * @author Juergen Hoeller
 * @since 2.5.1
 *
 * 在DefaultSingletonBeanRegistry的基础上增加了对FactoryBean的支持
 * 意思就是单独对FactoryBean创建单利对象，即是FactoryBean的单利注册表
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	/** Cache of singleton objects created by FactoryBeans: FactoryBean name --> object
	 * FactoryBeans创建的单例对象的缓存：FactoryBean名称 - >对象
     * FactoryBean对象都缓存在这里，同时,factoryBean的单利对象也会存放在DefaultSingletonBeanRegistry中的单利集合中
	 * */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<String, Object>(16);


	/**
	 * Determine the type for the given FactoryBean.
     * 获取指定FactoryBean实例的类型
	 * @param factoryBean the FactoryBean instance to check
	 * @return the FactoryBean's object type,
	 * or {@code null} if the type cannot be determined yet
	 */
	protected Class<?> getTypeForFactoryBean(final FactoryBean<?> factoryBean) {
		try {
			//是否开启jvm安全检测,开启了安全检测器的话,jvm只能访问自身域内的文件，需要授予特权才能访问指定的文件,默认jdk是关闭安全检测的
			if (System.getSecurityManager() != null) {
                //调用特权方法进行授权,表示能够读取FactoryBean这个类
                //如果开启了安全检测,却没有定义授权,则无法对这个资源进行获取或操作
				return AccessController.doPrivileged(new PrivilegedAction<Class<?>>() {
					@Override
					public Class<?> run() {
						return factoryBean.getObjectType();
					}
				}, getAccessControlContext());
			}
			else {
				return factoryBean.getObjectType();
			}
		}
		catch (Throwable ex) {
			// Thrown from the FactoryBean's getObjectType implementation.
			logger.warn("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * Obtain an object to expose from the given FactoryBean, if available
	 * in cached form. Quick check for minimal synchronization.
     * 获取要从给定FactoryBean公开的对象（如果在缓存形式中可用）。 快速检查最小同步。
     * 根据factoryBeanName获取FactoryBean对象
	 * @param beanName the name of the bean  factoryBeanName
	 * @return the object obtained from the FactoryBean,
	 * or {@code null} if not available
	 */
	protected Object getCachedObjectForFactoryBean(String beanName) {
	    //从缓存中根据beanName获取factoryBean对象
		Object object = this.factoryBeanObjectCache.get(beanName);
		return (object != NULL_OBJECT ? object : null);
	}

	/**
	 * Obtain an object to expose from the given FactoryBean.
     * 获取要从给定FactoryBean公开的对象。
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @param shouldPostProcess whether the bean is subject to post-processing 是否需要后置处理
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
	    //factoryBean是否是一个单利
        //containsSingleton(beanName) 从单利集合缓存中判断是否含有这个beanName
		if (factory.isSingleton() && containsSingleton(beanName)) {
			synchronized (getSingletonMutex()) {
				Object object = this.factoryBeanObjectCache.get(beanName);
				if (object == null) {
				    //
					object = doGetObjectFromFactoryBean(factory, beanName);
					// Only post-process and store if not put there already during getObject() call above
					// (e.g. because of circular reference processing triggered by custom getBean calls)
                    //只有在上面的getObject（）调用期间没有进行后处理和存储（例如，由于自定义getBean调用触发的循环引用处理）
					Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
					if (alreadyThere != null) {
						object = alreadyThere;
					}
					else {
						if (object != null && shouldPostProcess) {
						    //当前单利bean是否正在创建中
							if (isSingletonCurrentlyInCreation(beanName)) {
								// Temporarily return non-post-processed object, not storing it yet..
								return object;
							}
							//创建单利前的处理
							beforeSingletonCreation(beanName);
							try {
							    //获取后置处理过的FacotyBean对象,子类可以实现postProcessObjectFromFactoryBean
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							catch (Throwable ex) {
								throw new BeanCreationException(beanName,
										"Post-processing of FactoryBean's singleton object failed", ex);
							}
							finally {
							    //factoryBean创建后的处理
								afterSingletonCreation(beanName);
							}
						}
						//总的单利集合中包含这个factoryBean对象的话,则加入到factoryBeanObjectCache缓存中
						if (containsSingleton(beanName)) {
							this.factoryBeanObjectCache.put(beanName, (object != null ? object : NULL_OBJECT));
						}
					}
				}
				return (object != NULL_OBJECT ? object : null);
			}
		}
		else {
		    //获取factoryBean对象,如果没有就创建并返回
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			if (object != null && shouldPostProcess) {
				try {
				    //如果需要后置处理,则实现这个方法
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			return object;
		}
	}

	/**
	 * Obtain an object to expose from the given FactoryBean.
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
     * 获取FactoryBean对象,如果没有则创建，如果创建也失败了,则抛出BeanCreationException异常
	 */
	private Object doGetObjectFromFactoryBean(final FactoryBean<?> factory, final String beanName)
			throws BeanCreationException {

		Object object;
		try {
			if (System.getSecurityManager() != null) {
				AccessControlContext acc = getAccessControlContext();
				try {
					object = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
						@Override
						public Object run() throws Exception {
								return factory.getObject();
							}
						}, acc);
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				object = factory.getObject();
			}
		}
		catch (FactoryBeanNotInitializedException ex) {
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

		// Do not accept a null value for a FactoryBean that's not fully
		// initialized yet: Many FactoryBeans just return null then.
		if (object == null && isSingletonCurrentlyInCreation(beanName)) {
			throw new BeanCurrentlyInCreationException(
					beanName, "FactoryBean which is currently in creation returned null from getObject");
		}
		return object;
	}

	/**
	 * Post-process the given object that has been obtained from the FactoryBean.
     * 对从FactoryBean获取的给定对象进行后处理。
	 * The resulting object will get exposed for bean references.
     * 生成的对象将暴露给bean引用。
	 * <p>The default implementation simply returns the given object as-is.
     * 默认实现只是按原样返回给定的对象。
	 * Subclasses may override this, for example, to apply post-processors.
     * 子类可以覆盖它，例如，应用后处理器。因为该方法是protected，所以这个方法只能同包下才能实现覆盖
	 * @param object the object obtained from the FactoryBean.
	 * @param beanName the name of the bean
	 * @return the object to expose
	 * @throws org.springframework.beans.BeansException if any post-processing failed
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * Get a FactoryBean for the given bean if possible.
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance
	 * @return the bean instance as FactoryBean
	 * @throws BeansException if the given bean cannot be exposed as a FactoryBean
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return (FactoryBean<?>) beanInstance;
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
     * 从缓存中删除factoryBean单利对象
	 */
	@Override
	protected void removeSingleton(String beanName) {
	    //获取所有的单利信息,所以这里添加了互斥锁,保证getSingletonMutex()返回的对象的完整性
		synchronized (getSingletonMutex()) {
		    //先从用来保存所有单利对象的缓存中删除factoryBean对象
			super.removeSingleton(beanName);
			//再从用来缓存所有factoryBean单利对象的缓存中删除factoryBean对象
			this.factoryBeanObjectCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
     * 直接清除所有的单利缓存
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanObjectCache.clear();
		}
	}

	/**
	 * Return the security context for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the security context returned by this method.
	 * @see AccessController#getContext()
	 */
	protected AccessControlContext getAccessControlContext() {
		return AccessController.getContext();
	}

}
