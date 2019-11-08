package example;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;

public class Test {
	public static void main(String[] args) {
//		registerBeanTest();
//		loadBeanTest();

		aliasCircleTest();

	}

	public static void aliasCircleTest() {
		DefaultListableBeanFactory defaultListableBeanFactory = new DefaultListableBeanFactory();
		defaultListableBeanFactory.registerAlias("bean1","alias2");
		defaultListableBeanFactory.registerAlias("alias2","bean2");
		defaultListableBeanFactory.registerAlias("bean3","alias2");
//		defaultListableBeanFactory.registerAlias("bean2","bean1");
		String[] arr = defaultListableBeanFactory.getAliases("bean3");
		for (String s : arr) {
			System.out.println(s);
		}

	}

	public static void loadBeanTest() {
		//Spring加载资源并装配对象过程
		//1.定义Spring配置文件
		//2.通过Resource对象将Spring配置文件进行抽象，抽象成一个Resource对象
		//3.定义Bean工厂
		//4.定义XmlBeanDefinitionReader对象，并将工厂作为参数传递进去后供后续回调使用
		//5.通过定义XmlBeanDefinitionReader对象读取之前抽象出来的Resource对象（包含xml文件的解析过程）
		//6.IOC容器创建完毕，用户通过容器获得所需要的对象信息
		//7.当用户获取bean的时候 才真正的创建对象
		ClassPathResource resource = new ClassPathResource("bean.xml");
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
		reader.loadBeanDefinitions(resource);
//		factory.getBean("")

	}

	public static void registerBeanTest() {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
		annotationConfigApplicationContext.register(User.class);
		annotationConfigApplicationContext.refresh();
		User bean = annotationConfigApplicationContext.getBean(User.class);
		System.out.println(bean);
	}
}
