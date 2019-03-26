package per.springmvc.service;

import per.springmvc.annoation.MyService;

@MyService
public class MyTestService {
    public String query(String name) {
        return "my name is " + name;
    }
}
