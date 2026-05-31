package pokergame.engine;

import org.junit.jupiter.api.Test;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandHistoryDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.dto.PlayerHandResultDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.dto.PokerTableDTO;
import pokergame.server.domain.repository.IGameRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.PokerGameEngine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PokerGameEnginePersistenceTest {

    @Test
    public void botActionsAndParticipantsAreNotPersisted() {
        StubPlayerRepository playerRepository = new StubPlayerRepository();
        CapturingGameRepository gameRepository = new CapturingGameRepository();
        PokerGameEngine engine = new PokerGameEngine(playerRepository, gameRepository);

        engine.sitPlayerDown("Alice", 1000, 0);
        engine.sitPlayerDown("Bot_0", 1000, 1);

        engine.startNewHand();
        engine.executePlayerAction("Bot_0", "FOLD", 0);

        assertFalse(gameRepository.actions.stream().anyMatch(action -> action.playerId().equals("Bot_0")));
        assertFalse(gameRepository.participants.stream().anyMatch(participant -> participant.playerUsername().equals("Bot_0")));
        assertTrue(gameRepository.actions.stream().anyMatch(action -> action.playerId().equals("Alice")));
        assertTrue(gameRepository.participants.stream().anyMatch(participant -> participant.playerUsername().equals("Alice")));
    }

    private static class StubPlayerRepository implements IPlayerRepository {
        private final Map<String, PlayerProfileDTO> profiles = new HashMap<>();

        StubPlayerRepository() {
            PlayerProfileDTO alice = new PlayerProfileDTO(
                    "alice-id",
                    "Alice",
                    null,
                    "password-hash",
                    1000,
                    LocalDateTime.now()
            );
            profiles.put(alice.id(), alice);
            profiles.put(alice.username(), alice);
        }

        @Override
        public PlayerProfileDTO findProfileById(String id) {
            return profiles.get(id);
        }

        @Override
        public PlayerProfileDTO findProfileByUsername(String username) {
            return profiles.get(username);
        }

        @Override
        public void saveProfile(PlayerProfileDTO profile) {
            profiles.put(profile.id(), profile);
            profiles.put(profile.username(), profile);
        }

        @Override
        public void updateProfile(PlayerProfileDTO profile) {
            saveProfile(profile);
        }
    }

    private static class CapturingGameRepository implements IGameRepository {
        private final List<HandActionDTO> actions = new ArrayList<>();
        private final List<HandParticipantDTO> participants = new ArrayList<>();

        @Override
        public HandHistoryDTO findHandHistoryById(String id) {
            return null;
        }

        @Override
        public void saveHandHistory(HandHistoryDTO handHistory) {
        }

        @Override
        public HandActionDTO findHandActionById(String id) {
            return null;
        }

        @Override
        public void saveHandAction(HandActionDTO handAction) {
            actions.add(handAction);
        }

        @Override
        public HandParticipantDTO findHandParticipantById(String id) {
            return null;
        }

        @Override
        public void saveHandParticipant(HandParticipantDTO handParticipant) {
            participants.add(handParticipant);
        }

        @Override
        public PlayerProfileDTO findPlayerProfileById(String id) {
            return null;
        }

        @Override
        public void savePlayerProfile(PlayerProfileDTO playerProfile) {
        }

        @Override
        public PokerTableDTO findPokerTableById(String id) {
            return null;
        }

        @Override
        public void savePokerTable(PokerTableDTO pokerTable) {
        }

        @Override
        public int findOrCreatePokerTable(String name, String hosterId) {
            return 1;
        }

        @Override
        public List<PlayerHandResultDTO> findRecentHandsForPlayer(String playerId, int limit) {
            return List.of();
        }
    }
}
