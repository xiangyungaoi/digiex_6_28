package com.caetp.digiex.entity.mapper;

import com.caetp.digiex.entity.HistoryOrder;

import java.util.List;

public interface HistoryOrderMapper {
    int deleteByPrimaryKey(String openOrder);

    int insert(HistoryOrder record);

    int insertSelective(HistoryOrder record);

    HistoryOrder selectByPrimaryKey(String openOrder);

    int updateByPrimaryKeySelective(HistoryOrder record);

    int updateByPrimaryKey(HistoryOrder record);

    List<HistoryOrder> getHistoryOrderByLogin(Integer login);
}