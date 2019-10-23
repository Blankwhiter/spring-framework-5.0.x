package example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Test {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
		annotationConfigApplicationContext.register(User.class);
		annotationConfigApplicationContext.refresh();
		User bean = annotationConfigApplicationContext.getBean(User.class);
		System.out.println(bean);
	}
}
