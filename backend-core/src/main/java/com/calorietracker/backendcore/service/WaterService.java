package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.AddWaterRequest;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.WaterEntry;
import com.calorietracker.backendcore.repository.WaterEntryRepository;
import com.calorietracker.backendcore.web.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaterService {

    private final WaterEntryRepository repository;
    private final CurrentUserService currentUser;

    public WaterService(WaterEntryRepository repository, CurrentUserService currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<WaterEntry> byDate(LocalDate date) {
        AppUser me = currentUser.current();
        return repository.findByUserIdAndEntryDateOrderByCreatedAtAsc(me.getId(), date);
    }

    @Transactional
    public WaterEntry add(AddWaterRequest req) {
        AppUser me = currentUser.current();
        WaterEntry w = new WaterEntry();
        w.setUserId(me.getId());
        w.setEntryDate(req.date());
        w.setMl(req.ml());
        return repository.save(w);
    }

    @Transactional
    public void delete(Long id) {
        AppUser me = currentUser.current();
        WaterEntry w = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Water entry " + id + " not found"));
        if (!w.getUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Water entry " + id + " not found");
        }
        repository.delete(w);
    }
}
