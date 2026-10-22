import os
from PIL import Image, ImageDraw

res_base = r"C:\Users\chait\.gemini\antigravity-ide\scratch\spendly\app\src\main\res"

clean_fg = Image.open("clean_fg.png").convert("RGBA")
clean_mono = Image.open("clean_mono.png").convert("RGBA")

# Mipmap densities for Adaptive Icons (108dp base)
adaptive_sizes = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432
}

# Mipmap densities for Legacy Icons (48dp base)
legacy_sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

bg_color = (11, 19, 14, 255) # #0B130E deep dark emerald background

for folder, size in adaptive_sizes.items():
    dest_dir = os.path.join(res_base, folder)
    os.makedirs(dest_dir, exist_ok=True)
    
    # 1. ic_launcher_foreground.png (108dp density size)
    fg_resized = clean_fg.resize((size, size), Image.Resampling.LANCZOS)
    fg_resized.save(os.path.join(dest_dir, "ic_launcher_foreground.png"), "PNG")
    
    # 2. ic_launcher_monochrome.png (108dp density size)
    mono_resized = clean_mono.resize((size, size), Image.Resampling.LANCZOS)
    mono_resized.save(os.path.join(dest_dir, "ic_launcher_monochrome.png"), "PNG")
    
    print(f"Generated adaptive foreground & monochrome for {folder} ({size}x{size})")

for folder, size in legacy_sizes.items():
    dest_dir = os.path.join(res_base, folder)
    os.makedirs(dest_dir, exist_ok=True)
    
    # 3. Legacy square/squircle ic_launcher.png
    legacy_bg = Image.new("RGBA", (size, size), bg_color)
    # The S symbol fits nicely with margin
    legacy_s = clean_fg.resize((size, size), Image.Resampling.LANCZOS)
    legacy_bg.alpha_composite(legacy_s)
    legacy_bg.save(os.path.join(dest_dir, "ic_launcher.png"), "PNG")
    
    # 4. Legacy round ic_launcher_round.png (circular mask)
    round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_draw = ImageDraw.Draw(round_img)
    round_draw.ellipse((0, 0, size, size), fill=bg_color)
    round_img.alpha_composite(legacy_s)
    round_img.save(os.path.join(dest_dir, "ic_launcher_round.png"), "PNG")
    
    print(f"Generated legacy launcher icons for {folder} ({size}x{size})")

# 5. Master 512x512 icon in drawable
master = Image.new("RGBA", (512, 512), bg_color)
s_512 = clean_fg.resize((512, 512), Image.Resampling.LANCZOS)
master.alpha_composite(s_512)
master.save(os.path.join(res_base, "drawable", "ic_app_icon.png"), "PNG")
print("Saved 512x512 master in drawable/ic_app_icon.png")

print("All app icon mipmap assets generated successfully!")
