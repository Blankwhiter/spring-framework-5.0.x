/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory;

import org.springframework.lang.Nullable;

/**
 * ------ 总的来说 就是如果一个类实现了这个接口，那么这就类就变成了工厂类，提供bean实例，而不是一个bean实例本身.在基础代码中比较常见。
 *
 * 接口由{@link BeanFactory}中使用的对象实现 本身就是个别对象的工厂。
 * 如果bean实现了这一个接口，那么它被用作暴露对象的工厂，而不是直接作为将暴露自身的bean实例。
 *
 * Interface to be implemented by objects used within a {@link BeanFactory} which
 * are themselves factories for individual objects. If a bean implements this
 * interface, it is used as a factory for an object to expose, not directly as a
 * bean instance that will be exposed itself.
 *
 * 注： 一个bean实现了这个接口是不能被当作一个普通的bean.
 * FactoryBean 是被定义成一个bean的风格， 但是对象暴露给bean引用({@link #getObject()})总是它创建的对象.
 *
 * <p><b>NB: A bean that implements this interface cannot be used as a normal bean.</b>
 * A FactoryBean is defined in a bean style, but the object exposed for bean
 * references ({@link #getObject()}) is always the object that it creates.
 *
 * FactoryBean支持单利，原型 也可以懒加载或者在启动时加载。
 * {@link SmartFactoryBean}允许公开更细粒度的行为元数据。
 *
 * <p>FactoryBeans can support singletons and prototypes, and can either create
 * objects lazily on demand or eagerly on startup. The {@link SmartFactoryBean}
 * interface allows for exposing more fine-grained behavioral metadata.
 *
 *
 * 这个接口在框架内部被大量使用，例如给AOP （ProxyFactoryBean）或者 给JndiObjectFactoryBean使用。
 * 它可以用来自定义组件;然而，这只在基础结构代码中很常见。
 *
 * <p>This interface is heavily used within the framework itself, for example for
 * the AOP {@link org.springframework.aop.framework.ProxyFactoryBean} or the
 * {@link org.springframework.jndi.JndiObjectFactoryBean}. It can be used for
 * custom components as well; however, this is only common for infrastructure code.
 *
 * <p><b>{@code FactoryBean} is a programmatic contract. Implementations are not
 * supposed to rely on annotation-driven injection or other reflective facilities.</b>
 * {@link #getObjectType()} {@link #getObject()} invocations may arrive early in
 * the bootstrap process, even ahead of any post-processor setup. If you need access
 * other beans, implement {@link BeanFactoryAware} and obtain them programmatically.
 *
 * 最后，FactoryBean对象参与包含BeanFactory的 bean创建的同步。通常不需要内部的同步化，
 * 而不是为了延迟初始化FactoryBean本身(或类似的东西)
 *
 * <p>Finally, FactoryBean objects participate in the containing BeanFactory's
 * synchronization of bean creation. There is usually no need for internal
 * synchronization other than for purposes of lazy initialization within the
 * FactoryBean itself (or the like).
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 08.03.2003
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.aop.framework.ProxyFactoryBean
 * @see org.springframework.jndi.JndiObjectFactoryBean
 */
public interface FactoryBean<T> {

	/**
	 *
	 * ------------- 总的来说 ，这个方法返回一个由这个工厂管理的对象实例，如果这个是个bean还没初始化好，就返回空。
	 *
	 * 返回一个由这个工厂管理的对象实例。
	 * 与{@link BeanFactory}一样，这允许同时支持单例和原型设计模式。
	 * 如果这个FactoryBean尚未完全初始化调用(例如，因为它涉及到一个循环引用)，
	 * *抛出一个对应的{@link FactoryBeanNotInitializedException}BeanFactory还没初始化的异常。
	 *
	 * 从Spring 2.0开始，factorybean可以返回{@code null}空对象。工厂会认为这是正常的值来使用;
	 * 它在这种情况下，将不再抛出FactoryBeanNotInitializedException。
	 * 但是FactoryBean的实现是鼓励适当的抛出BeanFactory还没初始化的异常。
	 *
	 * Return an instance (possibly shared or independent) of the object
	 * managed by this factory.
	 * <p>As with a {@link BeanFactory}, this allows support for both the
	 * Singleton and Prototype design pattern.
	 * <p>If this FactoryBean is not fully initialized yet at the time of
	 * the call (for example because it is involved in a circular reference),
	 * throw a corresponding {@link FactoryBeanNotInitializedException}.
	 * <p>As of Spring 2.0, FactoryBeans are allowed to return {@code null}
	 * objects. The factory will consider this as normal value to be used; it
	 * will not throw a FactoryBeanNotInitializedException in this case anymore.
	 * FactoryBean implementations are encouraged to throw
	 * FactoryBeanNotInitializedException themselves now, as appropriate.
	 * @return an instance of the bean (can be {@code null})
	 * @throws Exception in case of creation errors
	 * @see FactoryBeanNotInitializedException
	 */
	@Nullable
	T getObject() throws Exception;

	/**
	 *
	 *  ------- 总的来说 返回此FactoryBean创建的对象类型
	 *
	 *  返回FactoryBean创建的对象类型，或{@code null}(如果事先不知道)。
	 *  这允许一个检查特定类型的bean没有实例化对象，例如autowiring自动装配。
	 *  在创建单例对象的情况下，此方法应尽量避免单例创建;
	 *  它应该提前估计类型。
	 *  对于原型，返回一个有意义的类型也是可取的。
	 *  这个方法被调用的时候 FactoryBean应该已完全初始化。它不能依赖于期间创建的状态初始化;当然，如果可用，它仍然可以使用这种状态。
	 *  注意: 自动装配将直接忽略返回的工厂bean
	 *   {@code null}。因此，强烈建议实施
	 *  使用FactoryBean的当前状态，正确地使用这个方法。
	 *   @return这个FactoryBean创建的对象的类型，
	 *  或{@code null}(如果调用时不知道)
	 *
	 * Return the type of object that this FactoryBean creates,
	 * or {@code null} if not known in advance.
	 * <p>This allows one to check for specific types of beans without
	 * instantiating objects, for example on autowiring.
	 * <p>In the case of implementations that are creating a singleton object,
	 * this method should try to avoid singleton creation as far as possible;
	 * it should rather estimate the type in advance.
	 * For prototypes, returning a meaningful type here is advisable too.
	 * <p>This method can be called <i>before</i> this FactoryBean has
	 * been fully initialized. It must not rely on state created during
	 * initialization; of course, it can still use such state if available.
	 * <p><b>NOTE:</b> Autowiring will simply ignore FactoryBeans that return
	 * {@code null} here. Therefore it is highly recommended to implement
	 * this method properly, using the current state of the FactoryBean.
	 * @return the type of object that this FactoryBean creates,
	 * or {@code null} if not known at the time of the call
	 * @see ListableBeanFactory#getBeansOfType
	 */
	@Nullable
	Class<?> getObjectType();

	/**
	 *
	 *  ----- 总的来说 判断是否单例，默认单例。
	 *  该工厂管理的对象是否为单例?
	 *  如果是(return true),getObject()总是返回同一个共享的实例，该对象会被BeanFactory缓存起来
	 *  如果是(return false),getObject()返回独立的实例
	 *  一般情况下返回true
	 *
	 * Is the object managed by this factory a singleton? That is,
	 * will {@link #getObject()} always return the same object
	 * (a reference that can be cached)?
	 * <p><b>NOTE:</b> If a FactoryBean indicates to hold a singleton object,
	 * the object returned from {@code getObject()} might get cached
	 * by the owning BeanFactory. Hence, do not return {@code true}
	 * unless the FactoryBean always exposes the same reference.
	 * <p>The singleton status of the FactoryBean itself will generally
	 * be provided by the owning BeanFactory; usually, it has to be
	 * defined as singleton there.
	 * <p><b>NOTE:</b> This method returning {@code false} does not
	 * necessarily indicate that returned objects are independent instances.
	 * An implementation of the extended {@link SmartFactoryBean} interface
	 * may explicitly indicate independent instances through its
	 * {@link SmartFactoryBean#isPrototype()} method. Plain {@link FactoryBean}
	 * implementations which do not implement this extended interface are
	 * simply assumed to always return independent instances if the
	 * {@code isSingleton()} implementation returns {@code false}.
	 * <p>The default implementation returns {@code true}, since a
	 * {@code FactoryBean} typically manages a singleton instance.
	 * @return whether the exposed object is a singleton
	 * @see #getObject()
	 * @see SmartFactoryBean#isPrototype()
	 */
	default boolean isSingleton() {
		return true;
	}

}
