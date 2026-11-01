package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Hu Ge
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Shop name
     */
    private String name;

    /**
     * Shop type ID
     */
    private Long typeId;

    /**
     * Shop images, separated by commas
     */
    private String images;

    /**
     * Business district, such as Lujiazui
     */
    private String area;

    /**
     * Address
     */
    private String address;

    /**
     * Longitude
     */
    private Double x;

    /**
     * Latitude
     */
    private Double y;

    /**
     * Average price, rounded to an integer
     */
    private Long avgPrice;

    /**
     * Sales volume
     */
    private Integer sold;

    /**
     * Number of comments
     */
    private Integer comments;

    /**
     * Rating from 1 to 5, stored multiplied by 10 to avoid decimals
     */
    private Integer score;

    /**
     * Opening hours, such as 10:00-22:00
     */
    private String openHours;

    /**
     * Creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;


    @TableField(exist = false)
    private Double distance;
}
