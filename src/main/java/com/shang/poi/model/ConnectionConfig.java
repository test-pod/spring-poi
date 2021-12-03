package com.shang.poi.model;

import javax.persistence.*;
import lombok.Data;

/**
 * Created by shangwei2009@hotmail.com on 2021/11/29 16:41
 */
@Data
@Table(name = "connection_config")
public class ConnectionConfig {
    @Id
    @Column(name = "id")
    @GeneratedValue(generator = "JDBC")
    private Integer id;

    @Column(name = "\"name\"")
    private String name;

    @Column(name = "config")
    private String config;

    @Column(name = "running")
    private Integer running;
}