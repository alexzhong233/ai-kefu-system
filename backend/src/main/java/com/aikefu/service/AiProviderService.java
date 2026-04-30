package com.aikefu.service;

import com.aikefu.entity.AiProvider;
import com.aikefu.mapper.AiProviderMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final AiProviderMapper providerMapper;

    public List<AiProvider> listAll() {
        return providerMapper.selectList(
                new QueryWrapper<AiProvider>().orderByAsc("model_type", "created_at")
        );
    }

    public List<AiProvider> listByModelType(String modelType) {
        return providerMapper.selectList(
                new QueryWrapper<AiProvider>()
                        .eq("model_type", modelType)
                        .orderByDesc("is_active")
                        .orderByAsc("created_at")
        );
    }

    public AiProvider getActiveByModelType(String modelType) {
        return providerMapper.selectOne(
                new QueryWrapper<AiProvider>()
                        .eq("model_type", modelType)
                        .eq("is_active", true)
        );
    }

    public AiProvider getByProviderId(String providerId) {
        return providerMapper.selectOne(
                new QueryWrapper<AiProvider>().eq("provider_id", providerId)
        );
    }

    public AiProvider create(AiProvider provider) {
        provider.setProviderId(UUID.randomUUID().toString());
        provider.setIsActive(false);
        provider.setDeleted(0);
        providerMapper.insert(provider);
        log.info("Created AI provider: id={}, name={}, type={}", provider.getProviderId(), provider.getName(), provider.getModelType());
        return provider;
    }

    public AiProvider update(Long id, AiProvider updated) {
        AiProvider existing = providerMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Provider not found: " + id);
        }
        existing.setName(updated.getName());
        existing.setProviderType(updated.getProviderType());
        existing.setApiKey(updated.getApiKey());
        existing.setBaseUrl(updated.getBaseUrl());
        existing.setModelName(updated.getModelName());
        existing.setExtraConfig(updated.getExtraConfig());
        providerMapper.updateById(existing);
        log.info("Updated AI provider: id={}", existing.getProviderId());
        return existing;
    }

    public boolean delete(Long id) {
        AiProvider provider = providerMapper.selectById(id);
        if (provider == null) return false;
        providerMapper.deleteById(id);
        log.info("Deleted AI provider: id={}", provider.getProviderId());
        return true;
    }

    @Transactional
    public AiProvider activate(Long id) {
        AiProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + id);
        }

        // Deactivate all providers of the same model_type
        providerMapper.deactivateAllByModelType(provider.getModelType());

        // Activate the target provider
        provider.setIsActive(true);
        providerMapper.updateById(provider);

        log.info("Activated AI provider: id={}, name={}, type={}",
                provider.getProviderId(), provider.getName(), provider.getModelType());
        return provider;
    }

    /**
     * Get extra_config value as String
     */
    public String getExtraConfigValue(AiProvider provider, String key, String defaultValue) {
        if (provider == null || provider.getExtraConfig() == null) return defaultValue;
        Object val = provider.getExtraConfig().get(key);
        return val != null ? val.toString() : defaultValue;
    }

    public int getExtraConfigInt(AiProvider provider, String key, int defaultValue) {
        String val = getExtraConfigValue(provider, key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public float getExtraConfigFloat(AiProvider provider, String key, float defaultValue) {
        String val = getExtraConfigValue(provider, key, null);
        if (val == null) return defaultValue;
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getExtraConfigBoolean(AiProvider provider, String key, boolean defaultValue) {
        String val = getExtraConfigValue(provider, key, null);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }
}
