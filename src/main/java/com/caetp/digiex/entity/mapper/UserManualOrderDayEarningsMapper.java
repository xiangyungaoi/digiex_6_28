package com.caetp.digiex.entity.mapper;

import com.caetp.digiex.dto.api.UserManualOrderDayEaringsDTO;
import com.caetp.digiex.dto.api.UserManualOrderEarningsDTO;
import com.caetp.digiex.entity.UserManualOrderDayEarnings;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserManualOrderDayEarningsMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserManualOrderDayEarnings record);

    int insertSelective(UserManualOrderDayEarnings record);

    UserManualOrderDayEarnings selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserManualOrderDayEarnings record);

    int updateByPrimaryKey(UserManualOrderDayEarnings record);

    UserManualOrderDayEarnings calculationUserDayProfit(Long login);

    List<UserManualOrderDayEaringsDTO> userDayProfitList(Integer login);

}