package com.reljicd.service.impl;

import com.reljicd.exception.NotEnoughProductsInStockException;
import com.reljicd.model.Product;
import com.reljicd.repository.ProductRepository;
import com.reljicd.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Shopping Cart is implemented with a Map and stored in the user session.
 *
 * @author Dusan
 */
@Service
@Scope(
        value = WebApplicationContext.SCOPE_SESSION,
        proxyMode = ScopedProxyMode.TARGET_CLASS
)
@Transactional
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ProductRepository productRepository;

    private final Map<Product, Integer> products = new HashMap<>();

    @Autowired
    public ShoppingCartServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * If the product is already in the cart, increase its quantity by one.
     * Otherwise, add it to the cart with a quantity of one.
     *
     * @param product product to add
     */
    @Override
    public void addProduct(Product product) {
        if (products.containsKey(product)) {
            products.replace(product, products.get(product) + 1);
        } else {
            products.put(product, 1);
        }
    }

    /**
     * If the product quantity is greater than one, decrease it by one.
     * If the product quantity is one, remove it from the cart.
     *
     * @param product product to remove
     */
    @Override
    public void removeProduct(Product product) {
        if (products.containsKey(product)) {
            int quantity = products.get(product);

            if (quantity > 1) {
                products.replace(product, quantity - 1);
            } else {
                products.remove(product);
            }
        }
    }

    /**
     * Returns an unmodifiable view of the products in the cart.
     *
     * @return products and their quantities
     */
    @Override
    public Map<Product, Integer> getProductsInCart() {
        return Collections.unmodifiableMap(products);
    }

    /**
     * Checkout rolls back if there is not enough stock for any product.
     *
     * @throws NotEnoughProductsInStockException when stock is insufficient
     */
    @Override
    public void checkout() throws NotEnoughProductsInStockException {

        for (Map.Entry<Product, Integer> entry : products.entrySet()) {

            Long productId = entry.getKey().getId();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Product not found with ID: " + productId
                            )
                    );

            int requestedQuantity = entry.getValue();

            if (product.getQuantity() < requestedQuantity) {
                throw new NotEnoughProductsInStockException(product);
            }

            entry.getKey().setQuantity(
                    product.getQuantity() - requestedQuantity
            );
        }

        productRepository.saveAll(products.keySet());
        productRepository.flush();
        products.clear();
    }

    /**
     * Calculates the total price of all products in the cart.
     *
     * @return cart total
     */
    @Override
    public BigDecimal getTotal() {
        return products.entrySet()
                .stream()
                .map(entry ->
                        entry.getKey()
                                .getPrice()
                                .multiply(
                                        BigDecimal.valueOf(entry.getValue())
                                )
                )
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
    }
}