# Java-NES-Emulator
A NES Emulator made in Java. Final project for Principles in Software Engineering

# Nintendo Emulation: Overall Framework and Functionality

The **Nintendo Entertainment System (NES)** was, and still is, an iconic console that set the foundation for future gaming systems. This project aims to emulate the NES using Java, showcasing how pattern designs can be employed for developing software for emulation purposes. The project operates at a low level, focusing on replicating the hardware of the system itself. By utilizing principles of emulation and the resources available to enthusiasts, this project allows the recreation and enjoyment of classic childhood games.

## NES Design

What made the NES groundbreaking was its simplicity and efficiency, which stemmed from its key components:

1. **Central Processing Unit (CPU)**  
   The NES features a custom 8-bit microprocessor derived from the Ricoh 2A03. While limited, it provides an effective set of instructions optimized for game logic execution.

2. **Picture Processing Unit (PPU)**  
   Responsible for rendering the video output, the PPU manages sprites, background tiling, and the color palette. It can display up to 64 sprites per frame and leverages a palette of 54 indexed colors out of 64 possible options.

3. **Audio Processing Unit (APU)**  
   The APU generates the NES's audio, including sounds and music. It includes five sound channels: two pulse wave generators, a triangle wave generator, a noise generator, and a delta modulation channel (DMC) for simple playback.

4. **Memory**  
   The NES architecture includes 2KB of onboard RAM and 2KB of video RAM (VRAM). Cartridge-based games often expanded this with additional ROM and RAM, which was accessed through memory mapping techniques. The system also supports memory mirroring to optimize available space.
