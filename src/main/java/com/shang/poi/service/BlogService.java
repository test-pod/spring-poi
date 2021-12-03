package com.shang.poi.service;

import com.shang.poi.model.Blog;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import com.shang.poi.dao.BlogMapper;

import java.util.List;

/**
  * Created by shangwei2009@hotmail.com on 2021/11/23 20:32
  */
@Service
public class BlogService{

    @Resource
    private BlogMapper blogMapper;

    public List<Blog> listAll() {
        return blogMapper.selectAll();
    }
}
