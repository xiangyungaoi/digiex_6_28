package com.caetp.digiex.entity.mapper;


import com.caetp.digiex.dto.api.UserManualOrderEarningsDTO;
import com.caetp.digiex.entity.UserManualOrderEarnings;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserManualOrderEarningsMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserManualOrderEarnings record);

    int insertSelective(UserManualOrderEarnings record);

    UserManualOrderEarnings selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserManualOrderEarnings record);

    int updateByPrimaryKey(UserManualOrderEarnings record);

    List<UserManualOrderEarningsDTO> UserOrderDayProfitList(@Param("login") Integer login, @Param("orderId") Integer orderId);
}