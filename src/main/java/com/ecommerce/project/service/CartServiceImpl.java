package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIexception;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repositories.CartItemRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ImageUrlUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    CartRepository cartRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private ImageUrlUtil imageUrlUtil;

    @Autowired
    ModelMapper modelMapper;

    @Override
    public CartDTO addProductToCart(Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new APIexception("Quantity must be greater than zero");
        }
        // Find existing cart or create one
        Cart cart = createCart();

        // Retrieve product details
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        // Perform validations
        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(
                productId,
                cart.getCartId()
        );

        if (cartItem != null) {
            throw new APIexception("Product" + product.getProductName() + "already exist in the cart");
        }

        if (product.getQuantity() == 0) {
            throw new APIexception(product.getProductName() + " is not available");
        }

        if (product.getQuantity() < quantity) {
            throw new APIexception("Please, make an order of the " + product.getProductName()
                    + " less than or equal to quantity " + product.getQuantity() + ".");
        }

        // Create cart item
        CartItem newCartItem = new CartItem();
        newCartItem.setProduct(product);
        newCartItem.setCart(cart);
        newCartItem.setQuantity(quantity);
        newCartItem.setDiscount(product.getDiscount());
        newCartItem.setProductPrice(product.getSpecialPrice());

        // Save cart item
        cartItemRepository.save(newCartItem);

        cart.getCartItems().add(newCartItem);

        cart.setTotalPrice(cart.getTotalPrice().add(
                product.getSpecialPrice().multiply(BigDecimal.valueOf(quantity))));
        rotateCheckoutToken(cart);
        cartRepository.save(cart);

        // Return updated cart
        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<CartItem> cartItems = cart.getCartItems();
        Stream<ProductDTO> productDTOStream = cartItems.stream().map(item -> {
            ProductDTO map = modelMapper.map(item.getProduct(), ProductDTO.class);
            map.setQuantity(quantity);
            return map;
        });

        cartDTO.setProducts(productDTOStream.toList());

        return cartDTO;
    }

    @Override
    public List<CartDTO> getAllCarts() {
        List<Cart> carts = cartRepository.findAll();
        if (carts.isEmpty()) {
            throw new APIexception("No cart exist");
        }
        List<CartDTO> cartDTOS = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            List<CartItem> cartItems = cart.getCartItems();
            List<ProductDTO> products = cartItems.stream()
                    .map(item -> {
                        ProductDTO productDTO = modelMapper.map(item.getProduct(), ProductDTO.class);
                        productDTO.setQuantity(item.getQuantity());
                        return productDTO;
                    })
                    .toList();

            cartDTO.setProducts(products);
            return cartDTO;
        }).toList();
        return cartDTOS;
    }

    @Override
    public CartDTO getCart(String email, Long cartId) {
        Cart cart = cartRepository.findCartByEmailAndCartId(email, cartId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "cartId", cartId);
        }
        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
        List<ProductDTO> products = cart.getCartItems().stream().map(item -> {
                    ProductDTO productDTO = modelMapper.map(item.getProduct(), ProductDTO.class);
                    productDTO.setQuantity(item.getQuantity());
                    productDTO.setImage(imageUrlUtil.constructImageUrl(item.getProduct().getImage()));
                    return productDTO;
                })
                .toList();
        cartDTO.setProducts(products);

        return cartDTO;
    }

    @Transactional
    @Override
    public CartDTO updateProductQuantityInCart(Long productId, Integer quantity) {
        String emailId = authUtil.loggedInEmail();
        Cart userCart = cartRepository.findCartByEmail(emailId);
        if (userCart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }
        Long cartId = userCart.getCartId();

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (product.getQuantity() == 0) {
            throw new APIexception(product.getProductName() + " is not available");
        }

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(productId, cartId);
        if (cartItem == null) {
            throw new APIexception("Product " + product.getProductName() + " does not exist in the cart");
        }

        int newQuantity = cartItem.getQuantity() + quantity;

        if (newQuantity < 0) {
            throw new APIexception("Quantity cannot be negative");
        }

        if (newQuantity > 0 && product.getQuantity() < newQuantity) {
            throw new APIexception("Please, make an order of the " + product.getProductName()
                    + " less than or equal to quantity " + product.getQuantity() + ".");
        }

        if (newQuantity == 0) {
            deleteProductFromCart(cartId, productId);
        } else {
            cartItem.setProductPrice(product.getSpecialPrice());
            cartItem.setQuantity(newQuantity);
            cartItem.setDiscount(product.getDiscount());
            cart.setTotalPrice(cart.getTotalPrice().add(
                    cartItem.getProductPrice().multiply(BigDecimal.valueOf(quantity))));
            rotateCheckoutToken(cart);
            cartRepository.save(cart);
            cartItemRepository.save(cartItem);
        }

        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
        List<CartItem> cartItems = cart.getCartItems();
        List<ProductDTO> products = cartItems.stream()
                .map(item -> {
                    ProductDTO productDTO = modelMapper.map(item.getProduct(), ProductDTO.class);
                    productDTO.setQuantity(item.getQuantity());
                    return productDTO;
                })
                .toList();
        cartDTO.setProducts(products);
        return cartDTO;
    }

    @Transactional
    @Override
    public String deleteProductFromCart(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));
        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(productId, cartId);
        if (cartItem == null) {
            throw new ResourceNotFoundException("Product", "productId", productId);
        }
        cart.setTotalPrice(cart.getTotalPrice().subtract(
                cartItem.getProductPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))));
        rotateCheckoutToken(cart);
        cartItemRepository.deleteCartItemByProductIdAndCartId(productId, cartId);
        return "Product " + cartItem.getProduct().getProductName() + " has been removed from the cart";
    }

    @Transactional
    @Override
    public String deleteProductFromCurrentUserCart(Long productId) {
        String email = authUtil.loggedInEmail();
        Cart cart = cartRepository.findCartByEmail(email);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", email);
        }
        return deleteProductFromCart(cart.getCartId(), productId);
    }

    @Override
    public void updateProductInCarts(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(productId, cartId);

        if (cartItem == null) {
            throw new APIexception("Product " + product.getProductName() + " does not exist in the cart");
        }

        BigDecimal cartPrice = cart.getTotalPrice().subtract(
                cartItem.getProductPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));

        cartItem.setProductPrice(product.getSpecialPrice());

        cart.setTotalPrice(cartPrice.add(
                cartItem.getProductPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))));
        rotateCheckoutToken(cart);

        cartItemRepository.save(cartItem);
    }

    @Transactional
    @Override
    public String createOrUpdateCartWithItems(List<CartItemDTO> cartItems) {
        if (cartItems == null) {
            throw new APIexception("Cart items are required");
        }
        String emailId = authUtil.loggedInEmail();

        Cart existingCart = cartRepository.findCartByEmail(emailId);
        if (existingCart == null) {
            existingCart = new Cart();
            existingCart.setTotalPrice(BigDecimal.ZERO);
            existingCart.setUser(authUtil.loggedInUser());
            rotateCheckoutToken(existingCart);
            existingCart = cartRepository.save(existingCart);
        } else {
            cartItemRepository.deleteAllByCartId(existingCart.getCartId());
        }

        BigDecimal totalPrice = BigDecimal.ZERO;
        Set<Long> productIds = new HashSet<>();

        for (CartItemDTO cartItemDTO : cartItems) {
            Long productId = cartItemDTO.getProductId();
            Integer quantity = cartItemDTO.getQuantity();

            if (productId == null || quantity == null || quantity <= 0) {
                throw new APIexception("Every cart item needs a product and a quantity greater than zero");
            }
            if (!productIds.add(productId)) {
                throw new APIexception("A product can appear only once in the cart");
            }

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

            if (quantity > product.getQuantity()) {
                throw new APIexception("Requested quantity for " + product.getProductName()
                        + " exceeds available stock");
            }

            //product.setQuantity(product.getQuantity() - quantity);
            totalPrice = totalPrice.add(
                    product.getSpecialPrice().multiply(BigDecimal.valueOf(quantity)));

            CartItem cartItem = new CartItem();
            cartItem.setProduct(product);
            cartItem.setCart(existingCart);
            cartItem.setQuantity(quantity);
            cartItem.setDiscount(product.getDiscount());
            cartItem.setProductPrice(product.getSpecialPrice());
            cartItemRepository.save(cartItem);
        }

        existingCart.setTotalPrice(totalPrice);
        rotateCheckoutToken(existingCart);
        cartRepository.save(existingCart);

        return "Cart created/updated successfully with " + cartItems.size() + " items.";
    }

    private Cart createCart() {
        Cart userCart = cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if (userCart != null) {
            return userCart;
        }

        Cart cart = new Cart();
        cart.setTotalPrice(BigDecimal.ZERO);
        cart.setUser(authUtil.loggedInUser());
        rotateCheckoutToken(cart);
        Cart newCart = cartRepository.save(cart);
        return newCart;
    }

    private void rotateCheckoutToken(Cart cart) {
        cart.setCheckoutToken(UUID.randomUUID().toString());
    }
}
