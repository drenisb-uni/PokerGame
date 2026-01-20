import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class GameFrame extends JFrame implements ActionListener {

    private PokerGame pokerGame;

    private int boardWidth = 1024;
    private int boardHeight = 703;
    private int cardWidth = 110;
    private int cardHeight = 154;

    private ArrayList<Image> deckImg;

    JFrame frame = new JFrame("Poker");
    JPanel panel = new JPanel();

    JPanel playerSetupPanel = new JPanel();
    JLabel playerName = new JLabel("Player Name:");
    JTextField playerNameField = new JTextField(10);
    JButton submitButton = new JButton("Submit");
    JButton doneButton = new JButton("X");

    JPanel buttonPanel = new JPanel();
    JButton foldButton = new JButton("Fold");
    JButton callButton = new JButton("Call");
    JButton raiseButton = new JButton("Raise");

    public GameFrame(PokerGame pokerGame) {
        this.pokerGame = pokerGame;
        deckImg = new ArrayList<Image>();

//        for (int i = 0; i < 5; i++) {
//            if (pokerGame.communityCards[i] != null)
//                deckImg.add(new ImageIcon(pokerGame.communityCards[i].toImageString()).getImage());
//            else
//                deckImg.add(new ImageIcon("BACK.png").getImage());
//        }

        //frame setup
        frame.setSize(boardWidth, boardHeight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        panel.setLayout(new BorderLayout());
        panel.setBackground(new Color(53, 101, 77));

        frame.add(panel);

        //player setup panel
        playerSetupPanel.setLayout(new FlowLayout());
        playerSetupPanel.add(playerName);
        playerSetupPanel.add(playerNameField, BorderLayout.CENTER);

        submitButton.addActionListener(this);
        playerSetupPanel.add(submitButton, BorderLayout.LINE_END);

        doneButton.addActionListener(this);
        doneButton.setFocusable(false);
        doneButton.setEnabled(false);
        playerSetupPanel.add(doneButton, BorderLayout.LINE_END);

        panel.add(playerSetupPanel, BorderLayout.NORTH);

        //button panel setup
        buttonPanel.add(foldButton);
        buttonPanel.add(callButton);
        buttonPanel.add(raiseButton);
        foldButton.setFocusable(false);
        callButton.setFocusable(false);
        raiseButton.setFocusable(false);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    public void paint(Graphics g) {
        super.paint(g);
        draw(g);
    }

    public void draw(Graphics g) {
        g.drawImage(deckImg.get(0), 102, 240, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }
}
