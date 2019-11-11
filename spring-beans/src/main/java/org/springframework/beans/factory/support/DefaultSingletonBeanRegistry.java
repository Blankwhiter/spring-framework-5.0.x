/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.beans.factory.support;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * ----总的来说 该类实现了单例bean的注册，以及具备了别名的注册功能，可以让所有调用者通过bean名称 获得注册表中的bean实例。
 *
 * 共享bean实例的通用注册表，实现了SingletonBeanRegistry. 允许注册表中注册的单例应该被所有调用者共享，通过bean名称获得。
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * 还支持登记的DisposableBean实例，（这可能会或不能正确的注册单例），关闭注册表时destroyed.
 * 可以注册bean之间的依赖关系，执行适当的关闭顺序。
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * 这个类主要用作基类的BeanFactory实现， 提供基本的管理单例bean的实例功能。
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** Cache of singleton objects: bean name --> bean instance   存放单例对象的缓存 */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** Cache of singleton factories: bean name --> ObjectFactory 存放制造singleton的工厂对象的缓存   */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/** Cache of early singleton objects: bean name --> bean instance 存放singletonFactory 制造出来的 singleton 的缓存早期单例对象缓存 */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

	/** Set of registered singletons, containing the bean names in registration order 一组已注册的单例，包含按注册顺序排列的bean名称 */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/** Names of beans that are currently in creation 目前正在创建中的单例bean的名称的集合 */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Names of beans currently excluded from in creation checks 当前在创建检查中被排除在外的bean的名称 */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** List of suppressed Exceptions, available for associating related causes 存放异常出现的相关的原因的集合 */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/** Flag that indicates whether we're currently within destroySingletons  指示我们目前是否正在销毁单例的标志 */
	private boolean singletonsCurrentlyInDestruction = false;

	/** Disposable bean instances: bean name --> disposable instance 存放一次性bean的缓存 */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/** Map between containing bean names: bean name --> Set of bean names that the bean contains
	 * 外部bean与被包含在外部bean的所有内部bean集合包含关系的缓存   */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** Map between dependent bean names: bean name --> Set of dependent bean names  指定bean与依赖指定bean的所有bean的依赖关系的缓存   */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** Map between depending bean names: bean name --> Set of bean names for the bean's dependencies
	 * 指定bean与创建这个bean所需要依赖的所有bean的依赖关系的缓存 */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);

//	SingletonFactories维护了这个beanName的ObjectFactory。ObjectFactory通过getObject方法获取到了earlySingletonBean，然后在由earlySingletonBean成为bean的实例。
//	各个SingletonObject之间的关系也是由几个map对象维护（containedBeanMap，dependentBeanMap，dependenciesForBeanMap）。
//	containedBeanMap(被包含关系:key被value所包含)：key是被包含的bean, value则是包含该Bean的所有的bean。(在发现销毁时:value也要被销毁）
//	dependentBeanMap(被依赖关系:key被value锁依赖):key是被依赖的bean，value则是依赖于该bean的所有bean。（在发生销毁时：value要先于bean被销毁）
//	dependenciesForBeanMap(依赖关系:key依赖于value)：key表示的bean依赖于value表示的Bean。
//	在注册两个bean包含关系的时候，同时要注册他们的依赖关系。

	/**
	 *  SingletonBeanRegistry接口的registerSingleton方法的实现
	 *
	 * @param beanName the name of the bean
	 * @param singletonObject the existing singleton object
	 * @throws IllegalStateException
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				//1.如果singletonObjects缓存找到有指定名称为beanName的对象，则表示该名称已被占用
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			//2.若该名称没被占用，真正的注册操作在这里实现
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 *
	 * 将给定的单例对象添加到工厂的单例缓存中。
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			//添加至单例bean的缓存中
			this.singletonObjects.put(beanName, singletonObject);
			// beanName已被注册存放在singletonObjects缓存，那么singletonFactories不应该再持有名称为beanName的工厂
			this.singletonFactories.remove(beanName);
			// beanName已被注册存放在singletonObjects缓存，那么earlySingletonObjects不应该再持有名称为beanName的bean
			this.earlySingletonObjects.remove(beanName);
			// beanName放进单例注册表中
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 添加 名称为beanName的singletonFactory对象
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
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

	/**
	 *  SingletonBeanRegistry接口的getSingleton方法的实现
	 * @param beanName the name of the bean to look for
	 * @return
	 */
	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 *  返回在给定名称下注册的(原始)单例对象。
	 *  检查已经实例化的单例并允许对当前创建的单例的早期引用(解析循环引用)。
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		//获取单例对象执行流程：首先从单例对象缓存中获取对象，
		// 如果获取不到或者单例对象正在被并发创建，那么尝试从提前暴露的单例对象缓存中获取对象，
		// 如果还是获取不到，再从单例对象工厂中获取对象，并放入提前暴露的对象缓存中。

		//1.从单例对象缓存中获取对象
		Object singletonObject = this.singletonObjects.get(beanName);
		// 2.如果对象没有存在单例对象缓存中，或者处于正在创建中，
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			synchronized (this.singletonObjects) {
				//2.1 从提前暴露的单例对象缓存中获取对象
				singletonObject = this.earlySingletonObjects.get(beanName);
				//2.2 在提前暴露的单例对象也不存在 并且允许创建早期对象,则通过singletonFactories创建
				// 说明：（在注册成功之前，early singleton和beanFactory已经初始化好），
				//        能从early singleton中取到，直接返回，假如取不到，说明该bean还在单例工厂阶段，
				//        假如允许注册前可以被引用，可以直接去单例工厂取，无需等到注册完毕之后再取
				if (singletonObject == null && allowEarlyReference) {
					//2.3 从单例对象工厂中获取对象
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						//2.4 从单例对象工厂中获取对象，并放入提前暴露的对象缓存中，并从单例对象工厂中移除该对象
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		//3.返回单例对象（经过前面几个步骤都没有取到，说明该bean没有单例，返回null）
		return singletonObject;
	}

	/**
	 * 返回在给定名称下注册的(原始)单例对象，如果还没有注册，就创建并注册一个新的单例对象。
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		synchronized (this.singletonObjects) {

			//1.从单例对象缓存中获得单例对象
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				//2.beanName名称的单例不存在与单例对象缓存中
				if (this.singletonsCurrentlyInDestruction) {
					//2.1如果单例处于正在创建的状态，则抛出异常
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				//当用户debug状态 则打印如下日志：正在创建名为beanName的单例bean的共享实例
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				//2.2 将beanName加入
				beforeSingletonCreation(beanName);
				//创建单例开始标识
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					//2.3 通过ObjectFactory来获取singleton的实例
					singletonObject = singletonFactory.getObject();
					//创建单例结束
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
					//2.4 单例创建之后的回调。
					afterSingletonCreation(beanName);
				}
				//2.5 创建成功，则注册实例
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			//3.返回单例对象
			return singletonObject;
		}
	}

	/**
	 * 注册一个在创建一个单例bean实例时被抑制的异常，例如一个临时的循环引用解析问题。
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
	 * 移除指定bean的单例，实质上是移除几个缓存里面该bean对应的数据
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
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

	/**
	 * 判断该bean是否已经有注册的单例
	 * @param beanName the name of the bean to look for
	 * @return
	 */
	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	/**
	 * 获得已经注册的单例名称数组
	 * @return
	 */
	@Override
	public String[] getSingletonNames() {
		// 对singletonObjects加锁，可能是为了防止registeredSingletons和singletonObjects出现不一致的问题
		synchronized (this.singletonObjects) {
			//将set转成数组
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	/**
	 * 获得注册的单例大小
	 * @return
	 */
	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	/**
	 * 设置beanName是否在创建中
	 * 实际上是inCreationCheckExclusions集合添加删除操作
	 * @param beanName
	 * @param inCreation
	 */
	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	/**
	 * 指定的单例bean是否在创建中
	 * @param beanName
	 * @return
	 */
	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	/**
	 * 实际上指定的单例bean是否在创建中
	 * @param beanName
	 * @return
	 */
	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * 返回当前是否正在创建指定的单例bean（在整个工厂里面）
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 在单例创建之前回调。
	 * 默认实现将单例对象注册为当前正在创建的状态。
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		//判断当beanName不存在于创建检查排除集合中，并且往单例正在创建集合添加失败 则抛出当前bean创建异常。
		//（主要是为了标识 现在beanName开始创建之前正处于创建中。）
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * 单例创建之后的回调。
	 * 默认实现singletonCurrentlyInCreation集合移除正在创建的单例
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		//判断当beanName不存在于创建检查排除集合中，并且往单例正在创建集合删除失败 则抛出非法状态异常。
		//（主要是为了标识 现在beanName创建完成后 删除单例正在创建集合中的beanName。）
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * 将给定的bean添加到此注册中心的一次性bean列表中。
	 * Add the given bean to the list of disposable beans in this registry.
	 * 一次性使用的bean通常对应于注册的单例，
	 * 与bean名匹配，但可能是不同的实例
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * 注册两个bean之间的控制关系,例如内部bean和包含其的外部bean之间
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			// 从containedBeanMap缓存中查找外部bean名为containingBeanName的内部bean集合
			//computeIfAbsent ： jdk 8 以上 只有在当前 Map 中 key 对应的值不存在或为 null 时，调用 mappingFunction
			//eg:
			//			Set<String> containedBeans = this.containedBeanMap
			//					.get(containingBeanName);
			//			 如果没有，刚新建一个存放内部bean的集合，并且存放在containedBeanMap缓存中
			//			if (containedBeans == null) {
			//				containedBeans = new LinkedHashSet<String>(8);
			//				this.containedBeanMap.put(containingBeanName, containedBeans);
			//			}
			// 2.使用 computeIfAbsent 可以这样写
			//			Set<String> containedBeans =
			//					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			// 将名为containedBeanName的内部bean存放到内部bean集合
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		// 紧接着调用注册内部bean和外部bean的依赖关系的方法
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 注册给定bean的一个依赖bean，给定的bean销毁之前被销毁。
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		// 调用SimpleAliasRegistry的canonicalName方法，这方法是将参数beanName当做别名寻找到注册名(最原始的名称)，并依此递归
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {

			//computeIfAbsent道理同registerContainedBean一样
			// 从dependentBeanMap缓存中找到依赖名为canonicalName这个bean的 依赖bean集合
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			// 依赖bean集合添加参数指定的dependentBeanName
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			// 从dependenciesForBeanMap缓存中找到dependentBeanName要依赖的所有bean集合
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * 判断beanName跟dependentBeanName 是否存在依赖关系
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		//5.判断是否已经找过beanName，如果已经查找过 说明是不依赖的
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		//1.获取原始的beanName
		String canonicalName = canonicalName(beanName);
		//获取beaName的依赖集合
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}
		//2.从依赖集合中判断是否包含dependentBeanName，包含则证明已经处于注册依赖
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		//3.如果上面没有找到，则接着递归一直往下找（alreadySeen表示已经找到的beanName）
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			//4.将找过的beanName存入alreadySeen
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 确定是否还存在名为beanName的被依赖关系
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * 返回依赖于指定的bean的所有bean的名称，如果有的话。
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * 返回指定的bean依赖于所有的bean的名称，如果有的话。
	 * Return the names of all beans that the specified bean depends on, if any.
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
	 * 销毁单例
	 */
	public void destroySingletons() {
		if (logger.isDebugEnabled()) {
			logger.debug("Destroying singletons in " + this);
		}
		// 目前单例销毁标志开始
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		// 销毁一次性bean缓存中所有单例bean
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}
		// 清空依赖包含缓存
		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();
		// 清空单例缓存
		clearSingletonCache();
	}

	/**
	 * 清空在注册中心所有的单例缓存
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			//设置销毁标识结束
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * 销毁指定beanName的单例
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		//如果存在的话 就从singletonObjects，singletonFactories，earlySingletonObjects，registeredSingletons四个缓存中删除
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		//销毁对应的一次性bean实例。
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		// 这段代码告诉我们先移除要销毁依赖bean
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			//在完全同步的情况下，以保证断开连接的集合
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		// 销毁bean实例
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				logger.error("Destroy method on bean with name '" + beanName + "' threw an exception", ex);
			}
		}

		// Trigger destruction of contained beans...
		// 从containedBeanMap缓存中移除要销毁的bean，递归移除它的包含内部bean集合
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		// 从其它bean的依赖bean集合中移除要销毁的bean
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
		// 最后 从dependenciesForBeanMap缓存中移除要销毁的bean
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
