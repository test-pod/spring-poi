package com.shang.poi.model;

import javax.persistence.*;
import lombok.Data;

/**
  * Created by shangwei2009@hotmail.com on 2021/11/23 20:32
  */
@Data
@Table(name = "blog")
public class Blog {
    @Id
    @Column(name = "id")
    @GeneratedValue(generator = "JDBC")
    private Integer id;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;
}