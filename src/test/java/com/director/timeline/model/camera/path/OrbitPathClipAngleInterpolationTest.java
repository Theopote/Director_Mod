package com.director.timeline.model.camera.path;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 OrbitPathClip 的角度插值逻辑修复
 *
 * <p>设计语义：
 * - "顺时针" = 负方向旋转（俯视时右转），始终走最短路径
 * - "逆时针" = 正方向旋转（俯视时左转），始终走最短路径
 * - 10°→90° 顺时针 = 走最短 80° 但负方向 = -80°
 * - 10°→90° 逆时针 = 走最短 80° 但正方向 = +80°
 */
class OrbitPathClipAngleInterpolationTest {

    @Test
    void testClockwiseShortArc() {
        // 场景：从 10° 到 90°，顺时针（走最短路径 80°，负方向 = -80°）
        float start = 10f;
        float end = 90f;
        boolean clockwise = true;

        // t=0 应该在起点
        float angle0 = OrbitPathClip.interpolateOrbitAngle(start, end, 0f, clockwise);
        assertEquals(10f, angle0, 0.1f, "t=0 应该在起点 10°");

        // t=0.5 应该在中点（10° + (-40°) = -30°）
        float angle05 = OrbitPathClip.interpolateOrbitAngle(start, end, 0.5f, clockwise);
        assertEquals(10f - 40f, angle05, 0.1f, "t=0.5 应该顺时针走到 -30° (330°)");

        // t=1 应该在终点（10° + (-80°) = -70°，等价于 290°）
        float angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(10f - 80f, angle1, 0.1f, "t=1 应该顺时针走到 -70° (290°)");
    }

    @Test
    void testCounterClockwiseShortArc() {
        // 场景：从 10° 到 90°，逆时针（应该走短弧 +80°）
        float start = 10f;
        float end = 90f;
        boolean clockwise = false;

        // t=0 应该在起点
        float angle0 = OrbitPathClip.interpolateOrbitAngle(start, end, 0f, clockwise);
        assertEquals(10f, angle0, 0.1f, "t=0 应该在起点 10°");

        // t=0.5 应该在中点（10° 逆时针走 40° 到 50°）
        float angle05 = OrbitPathClip.interpolateOrbitAngle(start, end, 0.5f, clockwise);
        assertEquals(50f, angle05, 0.1f, "t=0.5 应该逆时针走到 50°");

        // t=1 应该在终点
        float angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(90f, angle1, 0.1f, "t=1 应该在终点 90°");
    }

    @Test
    void testClockwiseLongArc() {
        // 场景：从 90° 到 10°，顺时针（走最短路径 80°，负方向 = -80°）
        float start = 90f;
        float end = 10f;
        boolean clockwise = true;

        // t=0 应该在起点
        float angle0 = OrbitPathClip.interpolateOrbitAngle(start, end, 0f, clockwise);
        assertEquals(90f, angle0, 0.1f, "t=0 应该在起点 90°");

        // t=1 应该在终点（90° + (-80°) = 10°）
        float angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(10f, angle1, 0.1f, "t=1 应该顺时针走最短路径到 10°");
    }

    @Test
    void testFullCircleClockwise() {
        // 场景：从 0° 到 360°，顺时针（完整一圈）
        float start = 0f;
        float end = 360f;
        boolean clockwise = true;

        // t=0 应该在起点
        float angle0 = OrbitPathClip.interpolateOrbitAngle(start, end, 0f, clockwise);
        assertEquals(0f, angle0, 0.1f, "t=0 应该在起点 0°");

        // t=0.5 应该在中点（顺时针走半圈到 -180°）
        float angle05 = OrbitPathClip.interpolateOrbitAngle(start, end, 0.5f, clockwise);
        assertEquals(-180f, angle05, 0.1f, "t=0.5 应该顺时针走半圈到 -180°");

        // t=1 应该在终点（顺时针走整圈到 -360°）
        float angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(-360f, angle1, 0.1f, "t=1 应该顺时针走整圈到 -360°");
    }

    @Test
    void testFullCircleCounterClockwise() {
        // 场景：从 0° 到 360°，逆时针（完整一圈）
        float start = 0f;
        float end = 360f;
        boolean clockwise = false;

        // t=0 应该在起点
        float angle0 = OrbitPathClip.interpolateOrbitAngle(start, end, 0f, clockwise);
        assertEquals(0f, angle0, 0.1f, "t=0 应该在起点 0°");

        // t=0.5 应该在中点（逆时针走半圈到 180°）
        float angle05 = OrbitPathClip.interpolateOrbitAngle(start, end, 0.5f, clockwise);
        assertEquals(180f, angle05, 0.1f, "t=0.5 应该逆时针走半圈到 180°");

        // t=1 应该在终点（逆时针走整圈到 360°）
        float angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(360f, angle1, 0.1f, "t=1 应该逆时针走整圈到 360°");
    }

    @Test
    void testCrossingZero() {
        // 场景：从 350° 到 10°，逆时针（应该走短弧 +20°）
        float start = 350f;
        float end = 10f;
        boolean clockwise = false;

        // t=0 应该在起点
        float angle0 = OrbitPathClip.interpolateOrbitAngle(start, end, 0f, clockwise);
        assertEquals(350f, angle0, 0.1f, "t=0 应该在起点 350°");

        // t=0.5 应该在中点（350° 逆时针走 10° 到 360°，即 0°）
        float angle05 = OrbitPathClip.interpolateOrbitAngle(start, end, 0.5f, clockwise);
        assertEquals(360f, angle05, 0.1f, "t=0.5 应该逆时针走到 360° (0°)");

        // t=1 应该在终点（350° 逆时针走 20° 到 370°，即 10°）
        float angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(370f, angle1, 0.1f, "t=1 应该逆时针走到 370° (10°)");
    }

    @Test
    void testExactly180Degrees() {
        // 场景：从 0° 到 180°（正好 180°，短弧和长弧一样）
        // 逆时针：应该走 +180°
        float start = 0f;
        float end = 180f;
        boolean clockwise = false;

        float angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(180f, angle1, 0.1f, "从 0° 到 180° 逆时针应该走 +180°");

        // 顺时针：应该走 -180°
        clockwise = true;
        angle1 = OrbitPathClip.interpolateOrbitAngle(start, end, 1f, clockwise);
        assertEquals(-180f, angle1, 0.1f, "从 0° 到 180° 顺时针应该走 -180°");
    }
}
