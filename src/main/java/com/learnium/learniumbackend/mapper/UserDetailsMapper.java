package com.learnium.learniumbackend.mapper;

import com.learnium.learniumbackend.entity.response.UserDetails;
import com.learnium.learniumbackend.model.User;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedSourcePolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface UserDetailsMapper {

    UserDetailsMapper INSTANCE = Mappers.getMapper(UserDetailsMapper.class);

    UserDetails toUserDetails(User user);
}
