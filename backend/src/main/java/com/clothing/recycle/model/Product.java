package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 环保积分兑换商品 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    private Integer pointsCost;

    private Integer stock;

    @Column(length = 255)
    private String description;

    /** 图标 emoji，便于零素材演示 */
    @Column(length = 16)
    private String icon;
}
