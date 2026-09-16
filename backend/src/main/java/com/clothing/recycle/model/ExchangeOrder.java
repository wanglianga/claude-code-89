package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exchange_orders")
public class ExchangeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User resident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    private Integer quantity;
    private Integer totalPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExchangeStatus status = ExchangeStatus.ORDERED;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime deliveredAt;
}
