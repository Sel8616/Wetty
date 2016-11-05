/*
 * MIT License
 *
 * Copyright (c) 2016 Erlu Shang (sel8616@gmail.com/philshang@163.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package cn.sel.spretty.controller;

import cn.sel.spretty.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class TestController
{
    private final TestService service;

    @Autowired
    public TestController(TestService service)
    {
        Assert.notNull(service);
        this.service = service;
    }

    @RequestMapping(path = "home")
    public String html(HttpServletRequest request, HttpServletResponse response)
    {
        return "home";
    }

    @ResponseBody
    @RequestMapping(path = "msg", produces = "text/plain;charset=UTF-8")
    public String msg(HttpServletRequest request, HttpServletResponse response)
    {
        return service.getMessage();
    }

    @ResponseBody
    @RequestMapping(path = "obj")
    public Object obj(HttpServletRequest request, HttpServletResponse response)
    {
        return service.getObject();
    }
}