/*
 * Copyright 2002-2015 the original author or authors.
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

/**
 * Common interface for managing aliases. Serves as super-interface for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
 *
 * 用于管理别名的通用接口。 用作{@link org.springframework.beans.factory.support.BeanDefinitionRegistry}的超级父接口。
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 *
 * 定义了别名注册表
 *
 * 该接口定义了以下功能：
 * 1.为指定的bean注册别名
 * 2.获取别名
 * 3.判断是否包含某别名
 * 4.移除别名
 *
 */
public interface AliasRegistry {

	/**
	 * Given a name, register an alias for it.
	 * @param name the canonical name
	 * @param alias the alias to be registered
	 * @throws IllegalStateException if the alias is already in use
	 * and may not be overridden
	 * 为name注册一个别名,具体实现看SimpleAliasRegistry
	 */
	void registerAlias(String name, String alias);

	/**
	 * Remove the specified alias from this registry.
	 * @param alias the alias to remove
	 * @throws IllegalStateException if no such alias was found
	 * 移除指定的别名
	 */
	void removeAlias(String alias);

	/**
	 * Determine whether this given name is defines as an alias
	 * (as opposed to the name of an actually registered component).
	 * @param name the name to check
	 * @return whether the given name is an alias
	 * 判断给定的name是不是一个别名
	 */
	boolean isAlias(String name);

	/**
	 * Return the aliases for the given name, if defined.
	 * @param name the name to check for aliases
	 * @return the aliases, or an empty array if none
	 * 获取指定name的所有映射的别名
	 */
	String[] getAliases(String name);

}
