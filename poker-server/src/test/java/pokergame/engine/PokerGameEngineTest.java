package pokergame.engine;

import pokergame.GameContext;
import pokergame.dbinfrastructure.SqlPlayerRepository;
import pokergame.domain.repository.IPlayerRepository;
import pokergame.engine.commands.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PokerGameEngineTest {

    @Test
    public void testThreePlayerBettingRound() throws InterruptedException {
        // 1. Setup Engine and Processor on the "Server" side

        PokerGameEngine engine = GameContext.getPokerGameEngine();
        GameCommandProcessor processor = new GameCommandProcessor(engine);

        // 2. Simulate players joining the table
        engine.sitPlayerDown("Alice", 1000, 0);
        engine.sitPlayerDown("Bob", 1000, 1);
        engine.sitPlayerDown("Charlie", 1000, 2);

        engine.startNewHand(); // Transitions GameState to PRE_FLOP

        // 3. Fire a sequence of actions into the processor queue
        // Let's assume Alice is the first to act
        //pre-flop
        processor.queueCommand(new CallCommand("Alice"));
        processor.queueCommand(new RaiseCommand("Bob", 100));
        processor.queueCommand(new FoldCommand("Charlie"));


        // Wait brief moment for the background loop to empty out the queue
        Thread.sleep(500);

        // 4. Run Assertions to verify the state changed perfectly!
        assertEquals(100, engine.getHighestCurrentBet());
        assertTrue(engine.getPlayerByUsername("Charlie").isFolded());
        assertEquals(900, engine.getPlayerByUsername("Bob").getChipsOnTable());
        assertEquals(900, engine.getPlayerByUsername("Alice").getChipsOnTable());
    }

    @Test
    public void testFullGameLoopThroughShowdown() throws InterruptedException {
        // 1. Setup Engine and Processor on the "Server" side
        // Using a new instance to keep the test perfectly isolated and deterministic
//        pokergame.domain.repository.IPlayerRepository mockRepo;
//        mockRepo = org.mockito.Mockito.mock(IPlayerRepository.class);
//        PokerGameEngine engine = new PokerGameEngine(mockRepo);
        PokerGameEngine engine = GameContext.getPokerGameEngine();
        GameCommandProcessor processor = new GameCommandProcessor(engine);

        // 2. Sit down 3 players with initial chip counts
        // Dealer button defaults to Index 0 (Alice).
        // Blinds will be: Bob (Small Blind = $10), Charlie (Big Blind = $20)
        engine.sitPlayerDown("Alice", 1000, 0);
        engine.sitPlayerDown("Bob", 1000, 1);
        engine.sitPlayerDown("Charlie", 1000, 2);

        // Start Hand: Deals hole cards, takes blinds ($10 + $20 = $30 pot), sets highest bet to $20
        engine.startNewHand();

        // --- PHASE 1: PRE-FLOP BETTING ---
        // Action starts at Under the Gun (Alice, index 0)
        processor.queueCommand(new CallCommand("Alice"));       // Alice puts in $20 to call BB
        processor.queueCommand(new CallCommand("Bob"));         // Bob puts in $10 more to match $20
        processor.queueCommand(new CallCommand("Charlie"));     // Charlie checks (calls 0 additional)

        Thread.sleep(100); // Give background worker queue a moment to process and advance stage to FLOP

        // Assertions: Verify Pot size is $60 ($20 x 3) and stage advanced to FLOP
        assertEquals(60, engine.getPotSize());
        assertEquals(0, engine.getHighestCurrentBet()); // Round bet resets to 0 on new street
System.out.println("passed 1");
        // --- PHASE 2: FLOP BETTING ---
        // Post-flop actions start with first active player left of Dealer -> Bob (index 1)
        processor.queueCommand(new CallCommand("Bob"));         // Bob Checks ($0)
        processor.queueCommand(new RaiseCommand("Charlie", 50));// Charlie bets $50
        processor.queueCommand(new CallCommand("Alice"));       // Alice calls $50
        processor.queueCommand(new CallCommand("Bob"));         // Bob calls $50

        Thread.sleep(100); // Process queue and advance stage to TURN

        // Assertions: Pot adds $150 ($50 x 3). Total Pot = $60 + $150 = $210
        assertEquals(210, engine.getPotSize());

        // --- PHASE 3: TURN BETTING ---
        // Starts with Bob again
        processor.queueCommand(new CallCommand("Bob"));         // Bob Checks
        processor.queueCommand(new CallCommand("Charlie"));     // Charlie Checks
        processor.queueCommand(new RaiseCommand("Alice", 100)); // Alice bets $100
        processor.queueCommand(new FoldCommand("Bob"));         // Bob folds out of the hand!
        processor.queueCommand(new CallCommand("Charlie"));     // Charlie calls $100

        Thread.sleep(100); // Process queue and advance stage to RIVER

        // Assertions: Alice ($100) + Charlie ($100) added $200. Total Pot = $210 + $200 = $410
        assertEquals(410, engine.getPotSize());
        assertTrue(engine.getPlayerByUsername("Bob").isFolded());

        // --- PHASE 4: RIVER BETTING ---
        // Bob is folded, so action shifts to Charlie
        processor.queueCommand(new CallCommand("Charlie"));     // Charlie Checks
        processor.queueCommand(new CallCommand("Alice"));       // Alice Checks behind

        Thread.sleep(100); // Process queue -> Triggers Showdown evaluation!

        // --- PHASE 5: SHOWDOWN & RESULTS ---
        // At this point, the engine executes evaluateShowdown(), hands are compared,
        // and the $410 pot is cleanly distributed to the winner(s).

        // Final Assertions
        assertTrue(engine.getPotSize() == 0 || engine.getPotSize() == 410); // Depending on your endHand cleanup style

        // Check remaining active player chip balances to confirm chips were added/subtracted correctly
        int aliceFinalChips = engine.getPlayerByUsername("Alice").getChipsOnTable();
        int charlieFinalChips = engine.getPlayerByUsername("Charlie").getChipsOnTable();
        int bobFinalChips = engine.getPlayerByUsername("Bob").getChipsOnTable();

        // Bob dropped exactly $70 ($10 SB + $50 Flop + $10 Turn fold)
        assertEquals(930, bobFinalChips);

        // Verify total chip count remains stable across the room ($3000 total economy)
        assertEquals(3000, aliceFinalChips + charlieFinalChips + bobFinalChips,
                "Economy leak detected! Total table chips must equal $3000.");

        processor.stop(); // Shut down background execution thread safely
    }
}