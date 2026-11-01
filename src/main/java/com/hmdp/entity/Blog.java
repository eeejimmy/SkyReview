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
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * Merchant ID
     */
    private Long shopId;
    /**
     * User ID
     */
    private Long userId;
    /**
     * User icon
     */
    @TableField(exist = false)
    private String icon;
    /**
     * User name
     */
    @TableField(exist = false)
    private String name;
    /**
     * Whether the user has liked the post
     */
    @TableField(exist = false)
    private Boolean isLike;

    /**
     * Title
     */
    private String title;

    /**
     * Review photos, up to 9, separated by commas
     */
    private String images;

    /**
     * Review text
     */
    private String content;

    /**
     * Number of likes
     */
    private Integer liked;

    /**
     * Number of comments
     */
    private Integer comments;

    /**
     * Creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;


}
