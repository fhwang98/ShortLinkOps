package com.fhwang.shortlinkops.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.fhwang.shortlinkops.domain.Link;

@Mapper
public interface LinkMapper {

    int insert(Link link);

    Optional<Link> findByShortCode(@Param("shortCode") String shortCode);

    int existsByShortCode(@Param("shortCode") String shortCode);

    int incrementClickCount(@Param("shortCode") String shortCode);
}
