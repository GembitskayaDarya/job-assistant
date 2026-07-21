package com.darya.jobassistant.mapper;

public interface EntityMapper<E, ReqD, ResD> {

    E toEntity(ReqD request);

    void updateEntity(E entity, ReqD request);

    ResD toResponse(E entity);
}
