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
package cn.sel.spretty.interceptor;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AccessLogger implements HandlerInterceptor
{
    private static final Logger LOGGER = Logger.getAnonymousLogger();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception
    {
        i(request);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView)
            throws Exception
    {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception
    {
        o(request, response);
    }

    private void i(HttpServletRequest request)
    {
        String msg = String.format("%s -> %s\t[%s]\tHeaders:%s\tParameters:%s", request.getRemoteAddr(), request.getRequestURI(), request.getMethod(),
                getHeaders(request), request.getParameterMap());
        LOGGER.info(msg);
    }

    private void o(HttpServletRequest request, HttpServletResponse response)
    {
        String msg = String.format("%s\t[%s] -> %s \tStatus:%s\tHeaders:%s", request.getRequestURI(), request.getMethod(), request.getRemoteAddr(),
                response.getStatus(), getHeaders(response));
        LOGGER.info(msg);
    }

    private String getHeaders(HttpServletRequest request)
    {
        Enumeration<String> headerNames = request.getHeaderNames();
        List<String> result = new ArrayList<>();
        while(headerNames.hasMoreElements())
        {
            String name = headerNames.nextElement();
            result.add(String.format("%s=%s", name, getEnumerationString(request.getHeaders(name))));
        }
        return Arrays.toString(result.toArray());
    }

    private String getHeaders(HttpServletResponse response)
    {
        List<String> headers = response.getHeaderNames().stream().collect(Collectors.toList());
        for(int i = 0; i < headers.size(); i++)
        {
            String header = headers.get(i);
            headers.set(i, String.format("%s=%s", header, response.getHeader(header)));
        }
        return Arrays.toString(headers.toArray(new String[headers.size()]));
    }

    private String getEnumerationString(Enumeration enumeration)
    {
        List<String> result = new ArrayList<>();
        while(enumeration.hasMoreElements())
        {
            result.add(String.valueOf(enumeration.nextElement()));
        }
        return Arrays.toString(result.toArray());
    }
}