# 🃏 Poker Game Refactoring Project

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-UI%2FUX-blue?style=for-the-badge&logo=java&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-DB-00000F?style=for-the-badge&logo=mysql&logoColor=white)

> A robust, multiplayer Texas Hold'em Poker engine featuring a modern **JavaFX UI**, refactored into a strict **Model-View-Controller (MVC)** architecture.

## 📖 Project Overview

This project modernizes a legacy poker application by decoupling the "Business Logic" from the "Presentation." The most significant upgrade is the transition to **JavaFX**, enabling a professional-grade User Experience (UX) with smooth animations and CSS-based styling.

### Key Features
* **Modern UI/UX:** Built with **JavaFX & FXML**, allowing for a responsive layout that separates visual design from code.
* **Layered Architecture:** Strict separation between Domain, Engine, and UI.
* **Reactive Updates:** The JavaFX UI uses the **Observer Pattern** to react instantly to game events (e.g., updates the Pot label immediately when a player bets).
* **Persistence:** MySQL database for tracking bankrolls and hand histories.

---

## 🏗️ Architecture

| Layer | Responsibility | Key Classes |
| :--- | :--- | :--- |
| **Domain** | Pure Logic (Rules, Cards). | `HandRanker`, `Card` |
| **Engine** | State Management. | `PokerGameEngine` |
| **View (JavaFX)** | **FXML** layouts & **CSS** styling. | `TableLayout.fxml`, `GameController.java` |

---

## 🛠️ Built With

* **Language:** Java 17
* **UI Framework:** **JavaFX** (with FXML)
* **Database:** MySQL
* **Build Tool:** Maven
* **AI Assistance:** Google Gemini (Architecture & Refactoring)
