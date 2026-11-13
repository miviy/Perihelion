#version 120

varying vec2 uv;
uniform float aspect;
uniform float time;
uniform float size;
uniform vec2 center;
uniform vec2 pointer;
uniform float subdued;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + 1.0), f.x), f.y);
}

vec3 heat(float value) {
    return mix(vec3(0.85, 0.28, 0.065), vec3(1.0, 0.81, 0.52), clamp(value, 0.0, 1.0));
}

vec3 starfield(vec2 sky, float grid, float threshold) {
    vec2 cell = floor(sky * grid);
    float seed = hash(cell);
    vec2 spot = fract(sky * grid) - vec2(0.18 + hash(cell + 4.0) * 0.64,
                                                       0.18 + hash(cell + 7.0) * 0.64);
    // Keep the cores visible at every GUI scale, without enlarging distant stars.
    vec2 pixel = max(fwidth(sky * grid), vec2(0.001));
    vec2 q = spot / max(vec2(0.025), pixel * 0.70);
    float core = exp(-dot(q, q) * 1.5);
    float twinkle = 0.67 + 0.33 * sin(time * (0.7 + seed) + seed * 113.0);
    float bright = mix(0.13, 0.85, pow(hash(cell + 13.0), 3.0));
    vec2 rays = abs(spot) / max(pixel, vec2(0.01));
    float sparkle = exp(-min(rays.x, rays.y) * 4.0) * exp(-max(rays.x, rays.y) * 0.8);
    sparkle *= step(0.91, hash(cell + 13.0)) * 0.23 * pow(twinkle, 3.0);
    vec3 tint = mix(vec3(0.64, 0.76, 1.0), vec3(1.0, 0.85, 0.64), hash(cell + 19.0));
    return tint * (core * bright * twinkle + sparkle) * step(threshold, seed);
}

vec3 shootingStars(vec2 sky) {
    vec3 light = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float clock = time + float(i) * 5.7;
        float cycle = floor(clock / 11.5);
        float age = mod(clock, 11.5);
        vec2 key = vec2(cycle, float(i) + 71.0);
        vec2 start = vec2(aspect * (0.28 + hash(key) * 0.55), 0.06 + hash(key + 5.0) * 0.27);
        vec2 direction = normalize(vec2(1.0, 0.36 + hash(key + 9.0) * 0.25));
        vec2 q = sky - start - direction * age * 0.34;
        float along = dot(q, direction);
        float across = abs(dot(q, vec2(-direction.y, direction.x)));
        float pixel = max(fwidth(sky.y), 0.0004);
        float streak = exp(-across * across / (pixel * pixel * 0.48));
        streak *= exp(min(along, 0.0) / 0.043) * step(along, 0.0) * smoothstep(-0.15, -0.11, along);
        float head = exp(-dot(q, q) / (pixel * pixel * 2.0));
        float life = smoothstep(0.0, 0.16, age) * (1.0 - smoothstep(0.85, 1.35, age));
        light += vec3(0.76, 0.86, 1.0) * (streak * 0.50 + head * 0.72) * life;
    }
    return light;
}

float streams(float radius, float angle, float speed) {
    // Differential rotation and inward advection break up the concentric bands.
    float shear = angle - time * speed / pow(max(radius, 0.6), 1.35);
    vec2 flow = vec2(sin(shear) * 5.0, radius * 18.0 + cos(shear) * 2.4 + time * 0.36);
    float broad = noise(flow);
    float detail = noise(flow * vec2(2.1, 2.7) + broad * 2.0);
    float filament = 0.5 + 0.5 * sin(radius * 79.0 + broad * 10.0 + detail * 3.0 + time * 2.0);
    float knots = pow(0.5 + 0.5 * sin(shear * 3.0 + radius * 9.0), 5.0);
    return (0.30 + broad * 0.48 + detail * 0.19 + knots * 0.42) * (0.73 + 0.27 * filament);
}

vec3 infall(float r, float angle) {
    float light = 0.0;
    float pixel = max(fwidth(r), 0.002);
    // Bounded analytic arcs: each fleck spirals faster and inward, then fades at capture.
    for (int i = 0; i < 18; i++) {
        float seed = hash(vec2(float(i), 43.0));
        float phase = fract(time * (0.045 + seed * 0.026) + seed * 13.0);
        float headAngle = seed * 6.283185 + phase * 8.0;
        float delta = mod(angle - headAngle + 3.141593, 6.283185) - 3.141593;
        // Most fragments miss this short arc; avoid evaluating their kernels.
        if (delta > -0.44 && delta < 0.045) {
            float trailPhase = clamp(phase + delta / 8.0, 0.0, 1.0);
            float orbit = 3.25 - 2.40 * pow(trailPhase, 1.7);
            float distance = r - orbit;
            float tail = smoothstep(-0.43, -0.33, delta) * (1.0 - smoothstep(-0.015, 0.018, delta));
            tail *= exp(delta * 7.0) * exp(-distance * distance / (pixel * pixel * 0.85));
            float headRadius = 3.25 - 2.40 * pow(phase, 1.7);
            vec2 head = vec2(r - headRadius, delta * r);
            float core = exp(-dot(head, head) / (pixel * pixel * 2.0));
            float life = smoothstep(0.0, 0.12, phase) * (1.0 - smoothstep(0.91, 1.0, phase));
            light += (tail * 0.26 + core * 0.80) * life;
        }
    }
    return vec3(1.0, 0.70, 0.40) * light;
}

void main() {
    vec2 p = (uv - center) * vec2(aspect, 1.0) / size;
    float tilt = 0.10 + pointer.x * 0.021 + pointer.y * 0.012;
    p = mat2(cos(tilt), sin(tilt), -sin(tilt), cos(tilt)) * p;
    float r = length(p);
    float angle = atan(p.y, p.x);
    vec3 scene = mix(vec3(0.018, 0.023, 0.032), vec3(0.033, 0.039, 0.052), uv.x);
    vec2 sky = (uv - pointer * 0.0017 + p / (r * r + 0.3) * 0.0015) * vec2(aspect, 1.0);
    vec3 stars = starfield(sky, 65.0, 0.975) + starfield(sky + 7.0, 105.0, 0.988);
    stars += shootingStars((uv - pointer * 0.0017) * vec2(aspect, 1.0));
    // Fade the distant sky behind the silhouette, while retaining stars beside the controls.
    stars *= smoothstep(0.835, 0.85, r);

    float halo = exp(-abs(r - 1.0) * 3.2);
    scene += vec3(0.23, 0.105, 0.045) * halo * 0.38;
    scene += vec3(0.018, 0.027, 0.050) * exp(-r * r * 0.35);

    if (r < 3.5) {
        scene += infall(r, angle);
        vec2 disk = vec2(p.x, p.y / (0.255 + pointer.y * 0.016));
        float dr = length(disk);
        float da = atan(disk.y, disk.x);
        float window = smoothstep(0.98, 1.10, dr) * (1.0 - smoothstep(2.10, 2.95, dr));
        float density = streams(dr, da, 0.80);
        float falloff = exp(-max(dr - 1.0, 0.0) * 2.10);
        float doppler = 0.55 + 0.65 * smoothstep(-1.5, 1.5, p.x + pointer.x * 0.12);
        vec3 diskLight = heat(falloff * 0.9) * density * falloff * window * doppler * 2.10;
        float horizon = 1.0 - smoothstep(0.825, 0.839, r);
        scene += diskLight;
        scene = mix(scene, vec3(0.0025, 0.003, 0.0045), horizon);

        float lensWindow = smoothstep(0.836, 0.86, r) * (1.0 - smoothstep(1.07, 1.20, r));
        float top = 0.30 + 0.70 * (1.0 - smoothstep(-0.6, 0.3, p.y));
        float lensGrain = streams(r * 1.5, angle, 0.95);
        float spin = angle - time * 0.43;
        float curl = noise(vec2(sin(spin) * 6.0, r * 22.0 + cos(spin) * 2.0));
        float width = 0.035 + curl * 0.040;
        float offset = r - (0.925 + curl * 0.022);
        float lens = exp(-offset * offset / (width * width)) * lensWindow * top * lensGrain;
        scene += heat(0.72) * lens * 1.35;

        float ringDistance = abs(r - 0.843);
        float pixel = max(fwidth(r), 0.001);
        float ring = 1.0 - smoothstep(pixel * 0.55, pixel * 1.8 + 0.004, ringDistance);
        float light = 0.70 + 0.14 * sin(angle - 0.6) + 0.16 * sin(angle * 4.0 - time * 1.6);
        scene += vec3(1.0, 0.88, 0.67) * ring * light * 1.25;
        scene += heat(0.7) * exp(-ringDistance * 53.0) * light * 0.50;
        float nearSide = smoothstep(-0.01, 0.085, p.y);
        scene += diskLight * horizon * nearSide;
    }

    float veil = smoothstep(0.28, 0.60, uv.x);
    scene = mix(vec3(0.023, 0.028, 0.038), scene, veil);
    scene += stars * mix(0.65, 1.0, veil);
    scene *= 1.0 - 0.22 * smoothstep(0.35, 0.82, length(uv - 0.5));
    scene = vec3(1.0) - exp(-scene * 1.15);
    scene = mix(scene, vec3(0.027, 0.032, 0.042) + scene * 0.22, subdued * 0.85);
    gl_FragColor = vec4(scene, 1.0);
}
