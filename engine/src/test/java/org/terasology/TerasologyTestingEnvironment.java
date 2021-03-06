/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.nio.file.ShrinkWrapFileSystems;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.AssetFactory;
import org.terasology.asset.AssetManager;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.asset.sources.ClasspathSource;
import org.terasology.audio.AudioManager;
import org.terasology.audio.nullAudio.NullAudioManager;
import org.terasology.classMetadata.reflect.ReflectionReflectFactory;
import org.terasology.config.Config;
import org.terasology.engine.ComponentSystemManager;
import org.terasology.engine.CoreRegistry;
import org.terasology.engine.EngineTime;
import org.terasology.engine.TerasologyConstants;
import org.terasology.engine.TerasologyEngine;
import org.terasology.engine.Time;
import org.terasology.engine.bootstrap.EntitySystemBuilder;
import org.terasology.engine.modes.loadProcesses.LoadPrefabs;
import org.terasology.engine.module.ModuleManager;
import org.terasology.engine.module.ModuleManagerImpl;
import org.terasology.engine.module.ModuleSecurityManager;
import org.terasology.engine.paths.PathManager;
import org.terasology.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.prefab.PrefabData;
import org.terasology.entitySystem.prefab.internal.PojoPrefab;
import org.terasology.network.NetworkSystem;
import org.terasology.network.internal.NetworkSystemImpl;
import org.terasology.persistence.StorageManager;
import org.terasology.persistence.internal.StorageManagerInternal;
import org.terasology.physics.CollisionGroupManager;
import org.terasology.rendering.ShaderManager;
import org.terasology.rendering.assets.animation.MeshAnimation;
import org.terasology.rendering.assets.animation.MeshAnimationData;
import org.terasology.rendering.assets.animation.MeshAnimationImpl;
import org.terasology.rendering.assets.font.Font;
import org.terasology.rendering.assets.font.FontData;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.assets.material.MaterialData;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.assets.mesh.MeshData;
import org.terasology.rendering.assets.shader.Shader;
import org.terasology.rendering.assets.shader.ShaderData;
import org.terasology.rendering.assets.skeletalmesh.SkeletalMesh;
import org.terasology.rendering.assets.skeletalmesh.SkeletalMeshData;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureData;
import org.terasology.rendering.nui.skin.UISkin;
import org.terasology.rendering.nui.skin.UISkinData;
import org.terasology.rendering.opengl.GLSLMaterial;
import org.terasology.rendering.opengl.GLSLShader;
import org.terasology.rendering.opengl.OpenGLFont;
import org.terasology.rendering.opengl.OpenGLMesh;
import org.terasology.rendering.opengl.OpenGLSkeletalMesh;
import org.terasology.rendering.opengl.OpenGLTexture;
import org.terasology.utilities.NativeHelper;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.family.AlignToSurfaceFamilyFactory;
import org.terasology.world.block.family.DefaultBlockFamilyFactoryRegistry;
import org.terasology.world.block.family.HorizontalBlockFamilyFactory;
import org.terasology.world.block.internal.BlockManagerImpl;
import org.terasology.world.block.loader.WorldAtlas;
import org.terasology.world.block.shapes.BlockShape;
import org.terasology.world.block.shapes.BlockShapeData;
import org.terasology.world.block.shapes.BlockShapeImpl;

import java.nio.file.FileSystem;

import static org.mockito.Mockito.mock;

/**
 * A base class for unit test classes to inherit to run in a Terasology environment - with LWJGL set up and so forth
 *
 * @author Immortius
 */
public abstract class TerasologyTestingEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(TerasologyTestingEnvironment.class);

    private static boolean setup;

    private static BlockManager blockManager;
    private static Config config;
    private static AudioManager audioManager;
    private static CollisionGroupManager collisionGroupManager;
    private static ModuleManager moduleManager;
    private static NetworkSystem networkSystem;
    private static AssetManager assetManager;
    private EngineEntityManager engineEntityManager;
    private ComponentSystemManager componentSystemManager;
    private EngineTime mockTime;

    @BeforeClass
    public static void setupEnvironment() throws Exception {
        final JavaArchive homeArchive = ShrinkWrap.create(JavaArchive.class);
        final FileSystem vfs = ShrinkWrapFileSystems.newFileSystem(homeArchive);
        PathManager.getInstance().useOverrideHomePath(vfs.getPath(""));
        if (!setup) {
            setup = true;
            bindLwjgl();

            moduleManager = new ModuleManagerImpl(new ModuleSecurityManager());
            moduleManager.applyActiveModules();
            CoreRegistry.put(ModuleManager.class, moduleManager);

            assetManager = new AssetManager(moduleManager);
            CoreRegistry.put(AssetManager.class, assetManager);
            AssetType.registerAssetTypes(assetManager);
            assetManager.addAssetSource(new ClasspathSource(TerasologyConstants.ENGINE_MODULE, TerasologyEngine.class.getProtectionDomain().getCodeSource(),
                    TerasologyConstants.ASSETS_SUBDIRECTORY, TerasologyConstants.OVERRIDES_SUBDIRECTORY));
            assetManager.addAssetSource(new ClasspathSource("unittest", TerasologyTestingEnvironment.class.getProtectionDomain().getCodeSource(),
                    TerasologyConstants.ASSETS_SUBDIRECTORY, TerasologyConstants.OVERRIDES_SUBDIRECTORY));

            config = new Config();
            CoreRegistry.put(Config.class, config);

            Display.setDisplayMode(new DisplayMode(0, 0));
            Display.create(CoreRegistry.get(Config.class).getRendering().getPixelFormat());

            assetManager.setAssetFactory(AssetType.PREFAB, new AssetFactory<PrefabData, Prefab>() {

                @Override
                public Prefab buildAsset(AssetUri uri, PrefabData data) {
                    return new PojoPrefab(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.SHAPE, new AssetFactory<BlockShapeData, BlockShape>() {

                @Override
                public BlockShape buildAsset(AssetUri uri, BlockShapeData data) {
                    return new BlockShapeImpl(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.TEXTURE, new AssetFactory<TextureData, Texture>() {
                @Override
                public Texture buildAsset(AssetUri uri, TextureData data) {
                    return new OpenGLTexture(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.FONT, new AssetFactory<FontData, Font>() {
                @Override
                public Font buildAsset(AssetUri uri, FontData data) {
                    return new OpenGLFont(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.SHADER, new AssetFactory<ShaderData, Shader>() {
                @Override
                public Shader buildAsset(AssetUri uri, ShaderData data) {
                    return new GLSLShader(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.MATERIAL, new AssetFactory<MaterialData, Material>() {
                @Override
                public Material buildAsset(AssetUri uri, MaterialData data) {
                    return new GLSLMaterial(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.MESH, new AssetFactory<MeshData, Mesh>() {
                @Override
                public Mesh buildAsset(AssetUri uri, MeshData data) {
                    return new OpenGLMesh(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.SKELETON_MESH, new AssetFactory<SkeletalMeshData, SkeletalMesh>() {
                @Override
                public SkeletalMesh buildAsset(AssetUri uri, SkeletalMeshData data) {
                    return new OpenGLSkeletalMesh(uri, data);
                }
            });
            assetManager.setAssetFactory(AssetType.ANIMATION, new AssetFactory<MeshAnimationData, MeshAnimation>() {
                @Override
                public MeshAnimation buildAsset(AssetUri uri, MeshAnimationData data) {
                    return new MeshAnimationImpl(uri, data);
                }
            });
            CoreRegistry.get(AssetManager.class).setAssetFactory(AssetType.UI_SKIN, new AssetFactory<UISkinData, UISkin>() {
                @Override
                public UISkin buildAsset(AssetUri uri, UISkinData data) {
                    return new UISkin(uri, data);
                }
            });

            CoreRegistry.put(ShaderManager.class, new ShaderManager()).initShaders();

            DefaultBlockFamilyFactoryRegistry blockFamilyFactoryRegistry = new DefaultBlockFamilyFactoryRegistry();
            blockFamilyFactoryRegistry.setBlockFamilyFactory("horizontal", new HorizontalBlockFamilyFactory());
            blockFamilyFactoryRegistry.setBlockFamilyFactory("alignToSurface", new AlignToSurfaceFamilyFactory());
            blockManager = new BlockManagerImpl(new WorldAtlas(4096), blockFamilyFactoryRegistry);
            CoreRegistry.put(BlockManager.class, blockManager);

            audioManager = new NullAudioManager();

            CoreRegistry.put(AudioManager.class, audioManager);

            collisionGroupManager = new CollisionGroupManager();
            CoreRegistry.put(CollisionGroupManager.class, collisionGroupManager);
        } else {
            CoreRegistry.put(AssetManager.class, assetManager);
            CoreRegistry.put(BlockManager.class, blockManager);
            CoreRegistry.put(Config.class, config);
            CoreRegistry.put(AudioManager.class, audioManager);
            CoreRegistry.put(CollisionGroupManager.class, collisionGroupManager);
            CoreRegistry.put(ModuleManager.class, moduleManager);
        }
        PathManager.getInstance().setCurrentSaveTitle("world1");
    }

    @Before
    public void setup() throws Exception {
        CoreRegistry.put(ModuleManager.class, moduleManager);

        mockTime = mock(EngineTime.class);
        CoreRegistry.put(Time.class, mockTime);
        networkSystem = new NetworkSystemImpl(mockTime);
        CoreRegistry.put(NetworkSystem.class, networkSystem);
        engineEntityManager = new EntitySystemBuilder().build(CoreRegistry.get(ModuleManager.class), networkSystem, new ReflectionReflectFactory());
        CoreRegistry.put(StorageManager.class, new StorageManagerInternal(moduleManager, engineEntityManager));

        componentSystemManager = new ComponentSystemManager();
        CoreRegistry.put(ComponentSystemManager.class, componentSystemManager);
        LoadPrefabs prefabLoadStep = new LoadPrefabs();

        boolean complete = false;
        prefabLoadStep.begin();
        while (!complete) {
            complete = prefabLoadStep.step();
        }
        CoreRegistry.get(ComponentSystemManager.class).initialise();
    }

    @AfterClass
    public static void tearDown() {
        CoreRegistry.clear();
    }


    public EngineEntityManager getEntityManager() {
        return engineEntityManager;
    }

    public static void bindLwjgl() throws LWJGLException {
        switch (LWJGLUtil.getPlatform()) {
            case LWJGLUtil.PLATFORM_MACOSX:
                NativeHelper.addLibraryPath(PathManager.getInstance().getNativesPath().resolve("macosx"));
                break;
            case LWJGLUtil.PLATFORM_LINUX:
                NativeHelper.addLibraryPath(PathManager.getInstance().getNativesPath().resolve("linux"));
                if (System.getProperty("os.arch").contains("64")) {
                    System.loadLibrary("openal64");
                } else {
                    System.loadLibrary("openal");
                }
                break;
            case LWJGLUtil.PLATFORM_WINDOWS:
                NativeHelper.addLibraryPath(PathManager.getInstance().getNativesPath().resolve("windows"));

                if (System.getProperty("os.arch").contains("64")) {
                    System.loadLibrary("OpenAL64");
                } else {
                    System.loadLibrary("OpenAL32");
                }
                break;
            default:
                logger.error("Unsupported operating system: {}", LWJGLUtil.getPlatformName());
                System.exit(1);
        }
    }


}
