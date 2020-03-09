package cn.xbmchina.web.service;


import cn.xbmchina.entity.User;

import java.util.List;


public interface UserService {
    String get(String name);
    List<User> list();
}