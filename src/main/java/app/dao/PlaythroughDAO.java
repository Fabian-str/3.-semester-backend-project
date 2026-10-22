package app.dao;

import app.entities.Playthrough;

import java.util.List;

public interface PlaythroughDAO {

    Playthrough create(Playthrough playthrough);

    Playthrough findById(Long id);

    Playthrough findByIdAndUserId(Long id, Long userId);

    List<Playthrough> findAll();

    List<Playthrough> findByUserId(Long userId);

    Playthrough update(Playthrough playthrough);

    void delete(Long id);
}