package per.springmvc.servlet.v2;

import com.sun.deploy.net.HttpRequest;
import per.springmvc.annoation.*;
import per.springmvc.controller.MyTestController;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;


public class MyDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

    private Map<String, Method> handleMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req, resp);
        } catch (IOException | InvocationTargetException | IllegalAccessException e) {
            resp.getWriter().write("500,Exception details :" + e.getStackTrace());
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //加载相关类
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //扫描相关类
        doScanner(contextConfig.getProperty("scanPackage"));
        //初始化类的实例，并放入ioc容器
        doInstance();
        //完成依赖注入
        doAutowired();
        //初始化handlemapping容器
        initHandleMapping();
        System.out.println("init over");
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        url = url.replaceAll(req.getContextPath(), "").replaceAll("/+", "/");
        if (!handleMapping.containsKey(url)) {
            resp.getWriter().write("404");
        }
        Method method = handleMapping.get(url);

        Map<String, String[]> paramMap = req.getParameterMap();

        //形参
        Class<?>[] paramTypes = method.getParameterTypes();
        //赋值
        Object[] values = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            if (paramType == HttpServletRequest.class) {
                values[i] = req;
            } else if (paramType == HttpServletResponse.class) {
                values[i] = resp;
            } else {
                Annotation[] annotations = method.getParameterAnnotations()[i];
                for (Annotation annotation : annotations) {
                    if (annotation instanceof MyRequestParam) {
                        String value = ((MyRequestParam) annotation).value();
                        if (paramMap.containsKey(value)) {
                            String result = Arrays.toString(paramMap.get(value)).replaceAll("\\[|\\]", "").replaceAll("\\s", "");
                            Object obj = convert(result, paramType);
                            values[i] = obj;
                        }
                    }
                }
            }
        }
        String beanName = toLowerFirstChar(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), values);
    }

    private Object convert(String result, Class<?> paramType) {

        if (paramType == String.class) {
            return result;
        } else if (paramType == Integer.class) {
            return Integer.valueOf(result);
        } else if (paramType == Double.class) {
            return Double.valueOf(result);
        }
        return result;
    }

    private void initHandleMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entrySet : ioc.entrySet()) {
            Class<?> clazz = entrySet.getValue().getClass();
            if (!clazz.isAnnotationPresent(MyController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                baseUrl = clazz.getAnnotation(MyRequestMapping.class).value();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                    continue;
                }
                String url = ("/" + baseUrl + "/" + method.getAnnotation(MyRequestMapping.class).value()).replaceAll("/+", "/");
                handleMapping.put(url, method);
                System.out.println("Mapped " + url + "," + method);
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entrySet : ioc.entrySet()) {
            Field[] fields = entrySet.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(MyAutowired.class)) {
                    MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                    String name = autowired.value().trim();
                    if ("".equals(name)) {
                        name = toLowerFirstChar(field.getType().getSimpleName());
                    }
                    field.setAccessible(true);
                    try {
                        field.set(entrySet.getValue(), ioc.get(name));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }
    }

    private void doInstance() {

        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(MyController.class)) {
                    Annotation annotation = clazz.getAnnotation(MyController.class);
                    String name = ((MyController) annotation).value();
                    putInstanceToIoc(clazz, name);
                } else if (clazz.isAnnotationPresent(MyService.class)) {
                    Annotation annotation = clazz.getAnnotation(MyService.class);
                    String name = ((MyService) annotation).value();
                    putInstanceToIoc(clazz, name);
                } else {
                    continue;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void putInstanceToIoc(Class<?> clazz, String name) throws IllegalAccessException, InstantiationException {
        Object instance = clazz.newInstance();
        //默认首字母小写,如果自己定义用自定义的,也可以根据类型注入
        if ("".equals(name)) {
            name = toLowerFirstChar(clazz.getSimpleName());
        }
        ioc.put(name, instance);
        for (Class<?> anInterface : clazz.getInterfaces()) {
            if (ioc.containsKey(anInterface.getName())) {
                throw new RuntimeException("the base name:" + anInterface.getName() + " is exist");
            }
            ioc.put(anInterface.getName(), instance);
        }
    }

    private String toLowerFirstChar(String simpleName) {
        char[] charArray = simpleName.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

    private void doScanner(String path) {
        URL url = this.getClass().getClassLoader().getResource(path.replaceAll("\\.", "/"));
        File file = new File(url.getPath());
        for (File listFile : file.listFiles()) {
            if (listFile.isDirectory()) {
                doScanner(path + "." + listFile.getName());
                continue;
            }
            if (listFile.getName().endsWith(".class")) {
                classNames.add(path + "." + listFile.getName().replace(".class", ""));
            }
        }
    }

    private void doLoadConfig(String path) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(path);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
