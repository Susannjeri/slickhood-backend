package org.pms.silverocean.service.param;

import org.pms.silverocean.database.pms.ParamRepo;
import org.pms.silverocean.database.pms.entities.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ParamDao {
    private final ParamRepo paramRepo;

    public ParamDao(ParamRepo paramRepo) {
        this.paramRepo = paramRepo;
    }

    public void saveParam(Param param) {
        paramRepo.save(param);
    }

    public Set<Param> loadParamsForUser(Long userId) {
        return paramRepo.findAllByCreatedByAndActiveTrue(userId);
    }

    public Optional<Param> getParamByCreatedByAndId(long createdBy, Long paramId) {
        return paramRepo.findByCreatedByAndIdAndActiveTrue(createdBy, paramId);
    }

    public List<Param> getParamByGroupNameAndCreatedBy(String name, long createdBy) {
        return paramRepo.findByNameAndActiveTrueAndCreatedBy(name, createdBy);
    }

    public List<Param> getParamByGroupName(String name) {
        return paramRepo.findByNameAndActiveTrue(name);
    }

    public Page<String> getAllParams(Pageable pageable) {
        return paramRepo.getUniqueParamName(pageable);
    }

    public Page<String> getAllParamsWithFilter(Pageable pageable, String searchParam) {
        return paramRepo.getUniqueParamNameByNameOrEmail(pageable, searchParam, searchParam);
    }

    public void saveAllParams(List<Param> params) {
        paramRepo.saveAll(params);
    }


}
