<script setup lang="ts">
import { onMounted } from 'vue'
import { OaToastViewport } from '@openatom/ui'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

onMounted(() => auth.restore())
</script>

<template>
  <RouterView />
  <OaToastViewport />
</template>
