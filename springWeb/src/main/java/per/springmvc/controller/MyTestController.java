package per.springmvc.controller;

import per.springmvc.annoation.MyAutowired;
import per.springmvc.annoation.MyController;
import per.springmvc.annoation.MyRequestMapping;
import per.springmvc.annoation.MyRequestParam;
import per.springmvc.service.MyTestService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("test")
public class MyTestController {

    @MyAutowired
    private MyTestService myTestService;

    @MyRequestMapping("query")
    public void query(@MyRequestParam("name") String name, HttpServletRequest req, HttpServletResponse resp) {
        String str = myTestService.query(name);
        try {
            resp.getWriter().write(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MyRequestMapping("add")
    public void add(@MyRequestParam("a") Integer a, @MyRequestParam("b") Integer b, HttpServletRequest req, HttpServletResponse resp) {
        try {
            resp.getWriter().write(a + "+" + b + "=" + (a + b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MyRequestMapping("sub")
    public void sub(@MyRequestParam("a") Double a, HttpServletRequest req, @MyRequestParam("b") Double b, HttpServletResponse resp) {
        try {
            resp.getWriter().write(a + "-" + b + "=" + (a - b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
