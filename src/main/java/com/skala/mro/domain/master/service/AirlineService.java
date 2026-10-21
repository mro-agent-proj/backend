package com.skala.mro.domain.master.service;

import com.skala.mro.domain.master.dto.AirlineResponse;
import com.skala.mro.domain.master.repository.AirlineRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AirlineService {

    private final AirlineRepository airlineRepository;

    public List<AirlineResponse> list() {
        return airlineRepository.findAllByOrderByCode().stream().map(AirlineResponse::from).toList();
    }
}
