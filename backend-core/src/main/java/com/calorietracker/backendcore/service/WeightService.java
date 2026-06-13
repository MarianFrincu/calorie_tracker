package com.calorietracker.backendcore.service;

import com.calorietracker.backendcore.dto.AddWeightRequest;
import com.calorietracker.backendcore.model.AppUser;
import com.calorietracker.backendcore.model.WeightLog;
import com.calorietracker.backendcore.repository.WeightLogRepository;
import com.calorietracker.backendcore.web.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightService {

    private final WeightLogRepository repository;
    private final CurrentUserService currentUser;

    public WeightService(WeightLogRepository repository, CurrentUserService currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<WeightLog> list(LocalDate from, LocalDate to) {
        AppUser me = currentUser.current();
        if (from != null && to != null) {
            return repository.findByUserIdAndLogDateBetweenOrderByLogDateAsc(me.getId(), from, to);
        }
        return repository.findByUserIdOrderByLogDateAsc(me.getId());
    }

    /** Upserts the weight for the given date (one entry per user per day). */
    @Transactional
    public WeightLog upsert(AddWeightRequest req) {
        AppUser me = currentUser.current();
        WeightLog w = repository.findByUserIdAndLogDate(me.getId(), req.date())
                .orElseGet(() -> {
                    WeightLog n = new WeightLog();
                    n.setUserId(me.getId());
                    n.setLogDate(req.date());
                    return n;
                });
        w.setWeightKg(req.weightKg());
        return repository.save(w);
    }

    @Transactional
    public void delete(Long id) {
        AppUser me = currentUser.current();
        WeightLog w = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Weight entry " + id + " not found"));
        if (!w.getUserId().equals(me.getId())) {
            throw new ResourceNotFoundException("Weight entry " + id + " not found");
        }
        repository.delete(w);
    }
}
