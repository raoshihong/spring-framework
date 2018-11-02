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

package org.springframework.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Simple implementation of the {@link AliasRegistry} interface.
 * Serves as base class for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 *
 * 对AliasRegistry的实现，实现name->alias 的别名映射,主要就是将映射关系存放到Map中，alias作为key,name作为value
 *
 */
public class SimpleAliasRegistry implements AliasRegistry {

	/** Map from alias to canonical name
	 * 从别名映射到规范名称
	 * 数据格式：alias:name   所以同一个name可以有多个别名
	 * map的初始化大小最好是2^n次幂，所以这里是2^4
	 * */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<String, String>(16);


	/**
	 * 注册别名
	 * @param name the canonical name
	 * @param alias the alias to be registered
	 */
	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		//因为涉及到对aliasMap新增，删除，判断，获取,所以需要保证alias在同一时间内是原子性的,所以需要加锁
		synchronized (this.aliasMap) {
			if (alias.equals(name)) {
				//如果aliasMap中已存在alias的，会导致该alias的被移除
				this.aliasMap.remove(alias);
			}
			else {
				//根据别名获取注册的name,这里的name对应唯一的bean
				String registeredName = this.aliasMap.get(alias);
				//表示已经注册过别名了
				if (registeredName != null) {
					//如果已经注册的别名对应的name相等,则不需要重复注册
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register
						return;
					}
					//同一个别名,注册的name不一样,则判断是否可以覆盖,如果不可以,则抛出异常
					//如：name=a,alias=b name1=c,alias=b 允许覆盖,则aliasMap最终结果为{c:b}
					//是否允许别名覆盖，默认为允许覆盖
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot register alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
				}

				checkForAliasCircle(name, alias);
				this.aliasMap.put(alias, name);
			}
		}
	}

	/**
	 * Return whether alias overriding is allowed.
	 * Default is {@code true}.
	 * 当alias一样,name不一样时,是否允许覆盖,默认为true
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}

	/**
	 * Determine whether the given name has the given alias registered.
	 * 确定是否这个给定的name已经注册了指定的alias
	 * @param name the name to check
	 * @param alias the alias to look for
	 * @since 4.2.1
	 *
	 * 判断name和alias之间或者alias和name之间是否有映射关系
	 */
	public boolean hasAlias(String name, String alias) {
		for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
			//获取已注册的所有bean的name
			String registeredName = entry.getValue();
			//判断已注册过的bean的name是否有跟当前要注册别名的name相等
			/**
			 * aliasMap :   {mybeanalias:mybean,mybeanalias1:mybean,mybeanalias2:mybeanalias1}
			 */
			if (registeredName.equals(name)) {//这里的name可以是beanName也可以是别名
				//已注册过,则获取注册的别名,判断别名是已映射过,或者是别名之间带有传递依赖关系
				String registeredAlias = entry.getKey();
				//registeredName.equals(name) 和registeredAlias.equals(alias) 是用来交叉判断,比如name=a,alias=b 和name1=b,alias1=a这种情况,如果name.equal(alias1) and alias.equal(name1)这种情况是相互循环的,所以不允许注册
				//registeredName.equals(name) 和hasAlias(registeredAlias, alias) name=a,alias=b,name1=c,alias1=a这种情况,name.eqaul(alias1) and hasAlias(alias,name1),这种情况是判断
				if (registeredAlias.equals(alias) || hasAlias(registeredAlias, alias)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void removeAlias(String alias) {
		synchronized (this.aliasMap) {
			String name = this.aliasMap.remove(alias);
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	/**
	 * 判断name是不是一个别名
	 * @param name
	 * @return
	 */
	@Override
	public boolean isAlias(String name) {
		return this.aliasMap.containsKey(name);
	}

	/**
	 * 通过name获取映射的所有别名
	 * @param name
	 * @return
	 */
	@Override
	public String[] getAliases(String name) {
		List<String> result = new ArrayList<String>();
		synchronized (this.aliasMap) {
			retrieveAliases(name, result);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Transitively retrieve all aliases for the given name.
	 * 检索出给定的name的所有映射的别名，并保存到result list集合中
	 * @param name the target name to find aliases for
	 * @param result the resulting aliases list
	 *
	 * 检索出给定name的所有映射的别名
	 */
	private void retrieveAliases(String name, List<String> result) {
		for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
			String registeredName = entry.getValue();
			if (registeredName.equals(name)) {
				String alias = entry.getKey();
				result.add(alias);
				retrieveAliases(alias, result);
			}
		}
	}

	/**
	 * Resolve all alias target names and aliases registered in this
	 * factory, applying the given StringValueResolver to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 *
	 * 解析在此工厂中注册的所有别名目标名称和别名，将给定的StringValueResolver应用于它们。
	 * 例如，值解析器可以解析目标bean名称中的占位符，甚至可以解析别名中的占位符。
	 *
	 * 专门用来处理别名或者name中带有占位符的别名,有时候我们想动态使用别名,所以会使用到占位符,比如 my${beanName}alias,此时就需要处理${beanName}的占位符的值
	 * 这个方法的主要功能就是用来处理已注册的带有占位符的别名,将其转换为精确的名称,所以需要涉及到移除原来的带有占位符的别名,在添加替换后的别名
	 *
	 *
	 * @param valueResolver the StringValueResolver to apply
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		synchronized (this.aliasMap) {
			//拷贝已注册的所有别名
			Map<String, String> aliasCopy = new HashMap<String, String>(this.aliasMap);
			for (String alias : aliasCopy.keySet()) {
				//通过别名获取映射的name
				String registeredName = aliasCopy.get(alias);
				//处理别名中的占位符,并返回精确的别名
				String resolvedAlias = valueResolver.resolveStringValue(alias);
				//处理name中的占位符,并返回精确的name
				String resolvedName = valueResolver.resolveStringValue(registeredName);
				//下面的判断逻辑又跟registerAlias的逻辑差不多
				if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
					this.aliasMap.remove(alias);
				}
				else if (!resolvedAlias.equals(alias)) {
					//判断是否是否已存在对应的别名与name的映射,存在则只需要先删除,因为最后还是会加入的
					String existingName = this.aliasMap.get(resolvedAlias);
					if (existingName != null) {
						if (existingName.equals(resolvedName)) {
							// Pointing to existing alias - just remove placeholder
							this.aliasMap.remove(alias);
							break;
						}
						throw new IllegalStateException(
								"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
								"') for name '" + resolvedName + "': It is already registered for name '" +
								registeredName + "'.");
					}
					//检测是否存在循环引用
					checkForAliasCircle(resolvedName, resolvedAlias);
					//移除旧的注册的别名
					this.aliasMap.remove(alias);
					//添加已替换占位符的别名
					this.aliasMap.put(resolvedAlias, resolvedName);
				}
				else if (!registeredName.equals(resolvedName)) {
					this.aliasMap.put(alias, resolvedName);
				}
			}
		}
	}

	/**
	 * Check whether the given name points back to the given alias as an alias
	 * in the other direction already, catching a circular reference upfront
	 * and throwing a corresponding IllegalStateException.
	 * @param name the candidate name
	 * @param alias the candidate alias
	 * @see #registerAlias
	 * @see #hasAlias
	 */
	protected void checkForAliasCircle(String name, String alias) {
		//判断是否已经注册过
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * Determine the raw name, resolving aliases to canonical names.
	 * 确定原始名称，将别名解析为规范名称。
	 * @param name the user-specified name
	 * @return the transformed name
	 *
	 * 返回的是原始的beanName
	 *
	 */
	public String canonicalName(String name) {
		String canonicalName = name;
		// Handle aliasing...
		String resolvedName;
		do {
			//如果canonicalName是别名,则获取他的原始beanName
			resolvedName = this.aliasMap.get(canonicalName);
			if (resolvedName != null) {
				canonicalName = resolvedName;
			}
		}
		while (resolvedName != null);
		return canonicalName;
	}

}
