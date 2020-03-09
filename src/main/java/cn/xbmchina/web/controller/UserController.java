package cn.xbmchina.web.controller;
import cn.xbmchina.annotation.XXAutowired;
import cn.xbmchina.annotation.XXController;
import cn.xbmchina.annotation.XXRequestMapping;
import cn.xbmchina.annotation.XXRequestParam;
import cn.xbmchina.entity.User;
import cn.xbmchina.web.service.UserService;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@XXController
@XXRequestMapping("/user")
public class UserController {

    @XXAutowired
    private UserService userService;

    @XXRequestMapping("/index")
    public String index(HttpServletRequest request,HttpServletResponse response,
                        @XXRequestParam("name")String name) throws IOException{
        String res = userService.get(name);
        System.out.println(name+"=>"+res);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(res);
        return "index";
    }

    @XXRequestMapping("/list")
    public String list(HttpServletRequest request,HttpServletResponse response)
            throws IOException{
        List<User> users = userService.list();
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(users.toString());
        return "list";
    }
}