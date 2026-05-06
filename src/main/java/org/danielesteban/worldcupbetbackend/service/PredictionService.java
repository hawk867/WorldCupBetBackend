package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.entity.Prediction;
import org.danielesteban.worldcupbetbackend.domain.entity.User;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.PredictionRepository;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserRepository;
import org.danielesteban.worldcupbetbackend.service.exception.DuplicatePredictionException;
import org.danielesteban.worldcupbetbackend.service.exception.ForbiddenException;
import org.danielesteban.worldcupbetbackend.service.exception.PredictionLockedException;
import org.danielesteban.worldcupbetbackend.service.exception.ResourceNotFoundException;
import org.danielesteban.worldcupbetbackend.service.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    public PredictionService(PredictionRepository predictionRepository,
                             MatchRepository matchRepository,
                             UserRepository userRepository) {
        this.predictionRepository = predictionRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Prediction create(Long userId, Long matchId,
                             int homeGoals, int awayGoals,
                             Integer homePenalties, Integer awayPenalties) {
        validateGoals(homeGoals, awayGoals);

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        if (match.getStatus().isPredictionLocked()) {
            throw new PredictionLockedException("Predictions are locked for match: " + matchId);
        }

        if (predictionRepository.existsByUserIdAndMatchId(userId, matchId)) {
            throw new DuplicatePredictionException("Prediction already exists for match: " + matchId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Integer[] penalties = resolvePenalties(match, homeGoals, awayGoals, homePenalties, awayPenalties);

        return predictionRepository.save(Prediction.builder()
                .user(user)
                .match(match)
                .homeGoals(homeGoals)
                .awayGoals(awayGoals)
                .homePenalties(penalties[0])
                .awayPenalties(penalties[1])
                .build());
    }

    @Transactional
    public Prediction update(Long userId, Long predictionId,
                             int homeGoals, int awayGoals,
                             Integer homePenalties, Integer awayPenalties) {
        validateGoals(homeGoals, awayGoals);

        Prediction prediction = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prediction not found: " + predictionId));

        if (!prediction.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Prediction does not belong to user: " + userId);
        }

        Match match = prediction.getMatch();
        if (match.getStatus().isPredictionLocked()) {
            throw new PredictionLockedException("Predictions are locked for match: " + match.getId());
        }

        Integer[] penalties = resolvePenalties(match, homeGoals, awayGoals, homePenalties, awayPenalties);

        prediction.setHomeGoals(homeGoals);
        prediction.setAwayGoals(awayGoals);
        prediction.setHomePenalties(penalties[0]);
        prediction.setAwayPenalties(penalties[1]);
        return prediction;
    }

    @Transactional(readOnly = true)
    public List<Prediction> findByUser(Long userId) {
        return predictionRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Prediction> findByUserAndMatch(Long userId, Long matchId) {
        return predictionRepository.findByUserIdAndMatchId(userId, matchId);
    }

    private void validateGoals(int homeGoals, int awayGoals) {
        if (homeGoals < 0 || awayGoals < 0) {
            throw new ValidationException("Goals cannot be negative");
        }
    }

    private Integer[] resolvePenalties(Match match, int homeGoals, int awayGoals,
                                       Integer homePenalties, Integer awayPenalties) {
        boolean isKnockout = match.getStage().getOrderIdx() > 1;

        if (!isKnockout) {
            return new Integer[]{null, null};
        }

        if (homeGoals != awayGoals) {
            return new Integer[]{null, null};
        }

        if (homePenalties != null && awayPenalties != null) {
            if (homePenalties.equals(awayPenalties)) {
                throw new ValidationException("Penalty shootout cannot end in a draw");
            }
            return new Integer[]{homePenalties, awayPenalties};
        }

        return new Integer[]{null, null};
    }
}
