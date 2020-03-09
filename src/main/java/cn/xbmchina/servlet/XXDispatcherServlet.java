package cn.xbmchina.servlet;


import cn.xbmchina.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class XXDispatcherServlet extends HttpServlet{
    private Properties contextConfig = new Properties();
    private List<String> classNames = new ArrayList<String>();
    private Map<String, Object> ioc = new HashMap<String, Object>();
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    private static final long serialVersionUID = -4943120355864715254L;


    @Override
    public void init(ServletConfig config) throws ServletException {
        //load config
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //scan relative class
        doScanner(contextConfig.getProperty("scanPackage"));
        //init ioc container put relative class to it
        doInstance();
        //inject dependence
        doAutoWired();
        //init handlerMapping
        initHandlerMapping();
    }

    private void initHandlerMapping() {
        if(ioc.isEmpty())return;
        for(Map.Entry<String, Object> entry:ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(XXController.class)){continue;}
            String baseUrl = "";
            if(clazz.isAnnotationPresent(XXRequestMapping.class)){
                XXRequestMapping requestMapping = clazz.getAnnotation(XXRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            Method[] methods = clazz.getMethods();
            for(Method method:methods){
                if(!method.isAnnotationPresent(XXRequestMapping.class)){continue;}
                XXRequestMapping requestMapping = method.getAnnotation(XXRequestMapping.class);
                String url = (baseUrl+requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(url);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("mapped:"+url+"=>"+method);
            }
        }
    }

    private void doAutoWired() {
        if(ioc.isEmpty())return;
        for(Map.Entry<String, Object> entry:ioc.entrySet()){
            //依赖注入->给加了XXAutowired注解的字段赋值
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field:fields){
                if(!field.isAnnotationPresent(XXAutowired.class)){continue;}
                XXAutowired autowired = field.getAnnotation(XXAutowired.class);
                String beanName = autowired.value();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void doInstance() {
        if(classNames.isEmpty())return;
        try {
            for(String className:classNames){
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(XXController.class)){
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                }else if(clazz.isAnnotationPresent(XXService.class)){

                    XXService service = clazz.getAnnotation(XXService.class);
                    String beanName = service.value();
                    if("".equals(beanName)){
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for(Class<?> i:interfaces){
                        ioc.put(i.getName(), instance);
                    }
                }else{
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doScanner(String packageName) {
        URL resource =
                this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.", "/"));
        File classDir = new File(resource.getFile());
        for(File classFile:classDir.listFiles()){
            if(classFile.isDirectory()){
                doScanner(packageName+"."+classFile.getName());
            }else{
                String className = (packageName+"."+classFile.getName()).replace(".class", "");
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String location) {
        InputStream input = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            contextConfig.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            if(input!=null){
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        this.doPost(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        doDispatcher(req, res);
    }

    public void doDispatcher(HttpServletRequest req,HttpServletResponse res){
        try {
            Handler handler = getHandler(req);
            if(handler==null){
                res.getWriter().write("404 not found.");
                return;
            }
            Class<?>[] paramTypes = handler.method.getParameterTypes();
            Object[] paramValues = new Object[paramTypes.length];
            Map<String, String[]> params = req.getParameterMap();
            for(Entry<String, String[]> param:params.entrySet()){
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "");
                if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index],value);
            }
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int resIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[resIndex] = res;
            handler.method.invoke(handler.controller, paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

    }
    private Object convert(Class<?> type, String value) {
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
    private Handler getHandler(HttpServletRequest req){
        if(handlerMapping.isEmpty()){return null;}
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        for(Handler handler:handlerMapping){
            Matcher matcher = handler.pattern.matcher(url);
            if(!matcher.matches()){continue;}
            return handler;
        }
        return null;
    }
    private class Handler{
        protected Object controller;
        protected Method method;
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping;
        protected Handler(Pattern pattern,Object controller,Method method){
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }
        private void putParamIndexMapping(Method method) {
            Annotation[][] pa = method.getParameterAnnotations();
            for(int i=0;i<pa.length;i++){
                for(Annotation a:pa[i]){
                    if(a instanceof XXRequestParam){
                        String paramName = ((XXRequestParam)a).value();
                        if(!"".equals(paramName)){
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            for(int i=0;i<paramTypes.length;i++){
                Class<?> type = paramTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }
    }
}