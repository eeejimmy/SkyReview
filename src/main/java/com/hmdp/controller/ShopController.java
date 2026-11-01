package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * Web controller
 * </p>
 *
 * @author Hu Ge
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * Query shop information by ID
     * @param id Shop ID
     * @return Shop details
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * Add a shop
     * @param shop Shop data
     * @return Shop ID
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // Write to the database
        shopService.save(shop);
        // Return the shop ID
        return Result.ok(shop.getId());
    }

    /**
     * Update shop information
     * @param shop Shop data
     * @return Nothing
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // Write to the database
        return shopService.update(shop);
    }

    /**
     * Query shops by type with pagination
     * @param typeId Shop type
     * @param current Page number
     * @return Shop list
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
       return shopService.queryShopByType(typeId, current, x, y);
    }

    /**
     * Query shops by name keyword with pagination
     * @param name Shop name keyword
     * @param current Page number
     * @return Shop list
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // Query by type with pagination
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // Return the data
        return Result.ok(page.getRecords());
    }
}
